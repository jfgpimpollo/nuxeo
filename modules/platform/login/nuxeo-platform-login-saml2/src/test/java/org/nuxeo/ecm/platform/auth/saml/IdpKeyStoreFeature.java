/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.ecm.platform.auth.saml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.nuxeo.ecm.platform.auth.saml.SAMLFeature.formatXML;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.ClassRule;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.common.function.ThrowableSupplier;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.auth.saml.key.KeyManagerFeature;
import org.nuxeo.runtime.test.TemporaryKeyStore;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.SignableXMLObject;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.XMLParserException;

/**
 * @since 2023.19
 * @implNote The feature doesn't work with {@link KeyManagerFeature} due to an issue in Test Framework with guice
 */
public class IdpKeyStoreFeature implements RunnerFeature {

    private static final Logger log = LogManager.getLogger(IdpKeyStoreFeature.class);

    public static final String KEY_STORE_TYPE = "JKS";

    public static final String KEY_STORE_PASSWORD = "password";

    public static final String KEY_STORE_ENTRY_IDP_KEY = "idp";

    public static final String KEY_STORE_ENTRY_IDP_PASSWORD = "password";

    @ClassRule
    public static final TemporaryKeyStore TEMPORARY_KEY_STORE = new TemporaryKeyStore.Builder(KEY_STORE_TYPE,
            KEY_STORE_PASSWORD).generateKeyPair(KEY_STORE_ENTRY_IDP_KEY, KEY_STORE_ENTRY_IDP_PASSWORD).build();

    @Override
    public void start(FeaturesRunner runner) throws Exception {
        SAMLAuthenticationProvider.initOpenSAML();

        // load default idp metadata file
        var metadataURI = getClass().getResource("/idp-meta.xml").toURI();
        // unmarshall it to add the idp certificate
        EntityDescriptor entityDescriptor = unmarshallSAMLObject(IOUtils.toString(metadataURI, UTF_8));
        // prepare keyInfo certificate
        var x509KeyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
        x509KeyInfoGeneratorFactory.setEmitEntityCertificate(true);
        var credential = resolveIdpCredential();
        var keyInfo = x509KeyInfoGeneratorFactory.newInstance().generate(credential);
        // create KeyDescriptor which holds the certificate
        var keyDescriptor = //
                (KeyDescriptor) XMLObjectProviderRegistrySupport.getBuilderFactory()
                                                                .getBuilder(KeyDescriptor.DEFAULT_ELEMENT_NAME)
                                                                .buildObject(KeyDescriptor.DEFAULT_ELEMENT_NAME);
        keyDescriptor.setUse(UsageType.SIGNING);
        keyDescriptor.setKeyInfo(keyInfo);
        entityDescriptor.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol")
                        .getKeyDescriptors()
                        .add(keyDescriptor);
        // marshall the object to a metadata file
        var metadataCertifPath = Path.of(metadataURI).getParent().resolve("idp-meta-with-certificate.xml");
        try (var out = Files.newOutputStream(metadataCertifPath)) {
            formatXML(entityDescriptor, out);
        }
        log.debug("Generated idp-meta-with-certificate.xml file: {}",
                () -> ThrowableSupplier.asSupplier(() -> IOUtils.toString(metadataCertifPath.toUri(), UTF_8)).get());
    }

    public KeyStore getKeyStore() {
        return TEMPORARY_KEY_STORE.getKeyStore();
    }

    public String signSAMLObject(String samlString, SignatureType signatureType) {
        try {
            // init signature parameters
            var parameters = new SignatureSigningParameters();
            var credential = resolveIdpCredential();
            parameters.setSigningCredential(credential);
            parameters.setSignatureAlgorithm("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
            parameters.setSignatureCanonicalizationAlgorithm("http://www.w3.org/2001/10/xml-exc-c14n#");
            // unmarshall object to sign it
            var samlObject = unmarshallSAMLObject(samlString);
            // first sign assertions
            if (signatureType == SignatureType.ASSERTION || signatureType == SignatureType.BOTH) {
                samlObject.getOrderedChildren()
                          .stream()
                          .filter(child -> "Assertion".equals(child.getElementQName().getLocalPart()))
                          .forEach(ThrowableConsumer.asConsumer(
                                  assertion -> SignatureSupport.signObject((SignableXMLObject) assertion, parameters)));
            }
            // second sign response
            if (signatureType == SignatureType.RESPONSE || signatureType == SignatureType.BOTH) {
                SignatureSupport.signObject((SignableXMLObject) samlObject, parameters);
            }
            // finally marshall back
            String samlResponse = marshallSAMLMessage(samlObject);
            log.debug("Signed SAML object: {}", samlResponse);
            return samlResponse;
        } catch (ResolverException | SecurityException | MarshallingException | SignatureException e) {
            throw new NuxeoException("Unable to sign the saml object: " + samlString, e);
        }
    }

    protected Credential resolveIdpCredential() throws ResolverException {
        return new KeyStoreCredentialResolver(getKeyStore(),
                Map.of(KEY_STORE_ENTRY_IDP_KEY, KEY_STORE_ENTRY_IDP_PASSWORD)).resolveSingle(
                        new CriteriaSet(new EntityIdCriterion(KEY_STORE_ENTRY_IDP_KEY)));
    }

    @SuppressWarnings("unchecked")
    protected static <S extends SAMLObject> S unmarshallSAMLObject(String message) {
        try (var is = IOUtils.toInputStream(message, UTF_8)) {
            Document messageDoc = XMLObjectProviderRegistrySupport.getParserPool().parse(is);
            Element messageElem = messageDoc.getDocumentElement();

            Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
                                                                        .getUnmarshaller(messageElem);

            return (S) unmarshaller.unmarshall(messageElem);
        } catch (IOException | XMLParserException | UnmarshallingException e) {
            throw new AssertionError("Unable to unmarshall the message", e);
        }
    }

    protected static String marshallSAMLMessage(SAMLObject samlObject) {
        try {
            Marshaller marshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(samlObject);
            var transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            var out = new StringWriter();
            transformer.transform(new DOMSource(marshaller.marshall(samlObject)), new StreamResult(out));
            return out.toString();
        } catch (MarshallingException | TransformerException e) {
            throw new AssertionError("Unable to marshall the message", e);
        }
    }

    public enum SignatureType {
        RESPONSE, ASSERTION, BOTH;
    }
}
