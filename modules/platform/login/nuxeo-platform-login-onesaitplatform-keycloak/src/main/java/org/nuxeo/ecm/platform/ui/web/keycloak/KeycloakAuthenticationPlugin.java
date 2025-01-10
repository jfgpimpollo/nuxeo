/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     François Maturel
 */
package org.nuxeo.ecm.platform.ui.web.keycloak;

import static org.nuxeo.ecm.platform.ui.web.keycloak.KeycloakUserInfo.KeycloakUserInfoBuilder.aKeycloakUserInfo;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.spi.AuthChallenge;
import org.keycloak.adapters.spi.AuthOutcome;
import org.keycloak.representations.AccessToken;
import org.nuxeo.ecm.platform.api.login.UserIdentificationInfo;
import org.nuxeo.ecm.platform.ui.web.auth.interfaces.NuxeoAuthenticationPlugin;
import org.nuxeo.ecm.platform.ui.web.auth.interfaces.NuxeoAuthenticationPluginLogoutExtension;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.usermapper.service.UserMapperService;

/**
 * Authentication plugin for handling auth flow with Keyloack
 *
 * @since 7.4
 */

public class KeycloakAuthenticationPlugin
        implements NuxeoAuthenticationPlugin, NuxeoAuthenticationPluginLogoutExtension {

    private static final Logger log = LogManager.getLogger(KeycloakAuthenticationPlugin.class);

    private static final String PROTOCOL_CLASSPATH = "classpath:";

    public static final String KEYCLOAK_CONFIG_FILE_KEY = "keycloakConfigFilename";

    public static final String KEYCLOAK_MAPPING_NAME_KEY = "mappingName";

    public static final String DEFAULT_MAPPING_NAME = "keycloak";

    private String keycloakConfigFile = PROTOCOL_CLASSPATH + "keycloak.json";
    
    private static final String ADMINISTRATORS_MAPPING_PLATFORM_ROLES_ENV = "ADMINISTRATORS_MAPPING_PLATFORM_ROLES";
    private static final String MEMBERS_MAPPING_PLATFORM_ROLES_ENV = "MEMBERS_MAPPING_PLATFORM_ROLES";
    private static final String POWER_USERS_MAPPING_PLATFORM_ROLES_ENV = "POWER_USERS_MAPPING_PLATFORM_ROLES";
    
    private static final String ADMINISTRATORS_GROUP_NAME="administrators";
    private static final String MEMBERS_GROUP_NAME="members";
    private static final String POWER_USERS_GROUP_NAME="power users";

    protected KeycloakAuthenticatorProvider keycloakAuthenticatorProvider;

    private ThreadLocal<KeycloakRequestAuthenticator> localKeycloakAuthenticator = new ThreadLocal<>();

    protected String mappingName = DEFAULT_MAPPING_NAME;
    
    private Map<String, String> mRolesMapping;

    @Override
    public void initPlugin(Map<String, String> parameters) {
        log.info("INITIALIZE KEYCLOAK");

        if (parameters.containsKey(KEYCLOAK_CONFIG_FILE_KEY)) {
            keycloakConfigFile = PROTOCOL_CLASSPATH + parameters.get(KEYCLOAK_CONFIG_FILE_KEY);
        }

        if (parameters.containsKey(KEYCLOAK_MAPPING_NAME_KEY)) {
            mappingName = parameters.get(KEYCLOAK_MAPPING_NAME_KEY);
        }

        KeycloakDeployment kd;
        try (InputStream is = loadKeycloakConfigFile()) {
            kd = KeycloakNuxeoDeployment.build(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        keycloakAuthenticatorProvider = new KeycloakAuthenticatorProvider(new AdapterDeploymentContext(kd));
        log.info("Keycloak is using a per-deployment configuration loaded from: {}", keycloakConfigFile);
        
        
        this.mRolesMapping = new HashMap<String, String>();
        
        String administratorsEnv = System.getenv(ADMINISTRATORS_MAPPING_PLATFORM_ROLES_ENV);
        if(administratorsEnv != null && administratorsEnv.trim().length()>0) {
        	String[] roles = administratorsEnv.split(",");
        	for(int i=0; i < roles.length; i++) {
        		if(roles[i].trim().length()>0) {
        			this.mRolesMapping.put(roles[i].trim(), ADMINISTRATORS_GROUP_NAME);
        		}
        	}
        }
        
        String membersEnv = System.getenv(MEMBERS_MAPPING_PLATFORM_ROLES_ENV);
        if(membersEnv != null && membersEnv.trim().length()>0) {
        	String[] roles = membersEnv.split(",");
        	for(int i=0; i < roles.length; i++) {
        		if(roles[i].trim().length()>0) {
        			this.mRolesMapping.put(roles[i].trim(), MEMBERS_GROUP_NAME);
        		}
        	}
        }
        
        String powerUsersEnv = System.getenv(POWER_USERS_MAPPING_PLATFORM_ROLES_ENV);
        if(powerUsersEnv != null && powerUsersEnv.trim().length()>0) {
        	String[] roles = powerUsersEnv.split(",");
        	for(int i=0; i < roles.length; i++) {
        		if(roles[i].trim().length()>0) {
        			this.mRolesMapping.put(roles[i].trim(), POWER_USERS_GROUP_NAME);
        		}
        	}
        }
    }

    @Override
    public Boolean needLoginPrompt(HttpServletRequest httpRequest) {
        return Boolean.TRUE;
    }

    @Override
    public Boolean handleLoginPrompt(HttpServletRequest httpRequest, HttpServletResponse httpResponse, String baseURL) {
        KeycloakRequestAuthenticator authenticator = localKeycloakAuthenticator.get();
        try {
            if (authenticator != null) {
                AuthChallenge challenge = authenticator.getChallenge();
                if (challenge != null) {
                    if (authenticator.loginConfig == null) {
                        authenticator.loginConfig = authenticator.request.getContext().getLoginConfig();
                    }
                    if (challenge.getResponseCode() >= 400) {
                        if (authenticator.forwardToErrorPageInternal()) {
                            return Boolean.TRUE;
                        }
                    }
                    challenge.challenge(authenticator.getFacade());
                }
            }
            return Boolean.TRUE;
        } finally {
            localKeycloakAuthenticator.remove();
        }
    }

    @Override
    public List<String> getUnAuthenticatedURLPrefix() {
        // There are no unauthenticated URLs associated to login prompt.
        // If user is not authenticated, this plugin will have to redirect user to the keycloak sso login prompt
        return null;
    }

    @Override
    public UserIdentificationInfo handleRetrieveIdentity(HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        log.debug("KEYCLOAK will handle identification");

        KeycloakRequestAuthenticator authenticator = keycloakAuthenticatorProvider.provide(httpRequest, httpResponse);
        // need to keep the KeycloakRequestAuthenticator because it contains the challenge computed from the request
        localKeycloakAuthenticator.set(authenticator);
        KeycloakDeployment deployment = keycloakAuthenticatorProvider.getResolvedDeployment();
        String keycloakNuxeoApp = deployment.getResourceName();

        AuthOutcome outcome = authenticator.authenticate();

        if (outcome == AuthOutcome.AUTHENTICATED) {
            AccessToken token = (AccessToken) httpRequest.getAttribute(
                    KeycloakRequestAuthenticator.KEYCLOAK_ACCESS_TOKEN);

            KeycloakUserInfo keycloakUserInfo = getKeycloakUserInfo(token);

            UserMapperService ums = Framework.getService(UserMapperService.class);

            keycloakUserInfo.setRoles(getRoles(token, keycloakNuxeoApp));

            ums.getOrCreateAndUpdateNuxeoPrincipal(mappingName, keycloakUserInfo);

            return keycloakUserInfo;
        }
        return null;
    }

    @Override
    public Boolean handleLogout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        log.debug("KEYCLOAK will handle logout");

        String uri = keycloakAuthenticatorProvider.logout(httpRequest, httpResponse);
        try {
            httpResponse.sendRedirect(uri);
        } catch (IOException e) {
            String message = "Could note handle logout with URI: " + uri;
            log.error(message);
            throw new RuntimeException(message);
        }
        return Boolean.TRUE;
    }

    /**
     * Get keycloak user's information from authentication token
     *
     * @param token the keycoak authentication token
     * @return keycloak user's information
     */
    private KeycloakUserInfo getKeycloakUserInfo(AccessToken token) {
        return aKeycloakUserInfo()
                                  // Required
                                  .withUserName(token.getEmail())
                                  // Optional
                                  .withFirstName(token.getGivenName())
                                  .withLastName(token.getFamilyName())
                                  .withCompany(token.getPreferredUsername())
                                  // The password is randomly generated has we won't use it
                                  .withPassword(UUID.randomUUID().toString())
                                  .build();
    }

    /**
     * Get keycloak user's roles from authentication token
     *
     * @param token the keycoak authentication token
     * @param keycloakNuxeoApp the keycoak resource name
     * @return keycloak user's roles
     */
    private Set<String> getRoles(AccessToken token, String keycloakNuxeoApp) {
        Set<String> allRoles = new HashSet<>();

        AccessToken.Access realmAccess = token.getRealmAccess();
        if (realmAccess != null && realmAccess.getRoles() != null) {
            allRoles.addAll(realmAccess.getRoles());
        }

        AccessToken.Access nuxeoResource = token.getResourceAccess(keycloakNuxeoApp);
        if (nuxeoResource != null) {
            Set<String> nuxeoRoles = nuxeoResource.getRoles();
            allRoles.addAll(nuxeoRoles);
        }
        
        if(token.getOtherClaims().containsKey("role")) {
        	String role=token.getOtherClaims().get("role").toString();
        	allRoles.add(this.mRolesMapping.get(role));
        	
        }
        return allRoles;
    }

    /**
     * Loads Keycloak from configuration file
     *
     * @return the configuration file as an {@link InputStream}
     */
    @SuppressWarnings("resource")
    private InputStream loadKeycloakConfigFile() {

        if (keycloakConfigFile.startsWith(PROTOCOL_CLASSPATH)) {
            String classPathLocation = keycloakConfigFile.replace(PROTOCOL_CLASSPATH, "");

            log.debug("Loading config from classpath on location: {}", classPathLocation);

            // Try current class classloader first
            InputStream is = getClass().getClassLoader().getResourceAsStream(classPathLocation);
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(classPathLocation);
            }

            if (is != null) {
                return is;
            } else {
                String message = "Unable to find config from classpath: " + keycloakConfigFile;
                log.error(message);
                throw new RuntimeException(message);
            }
        } else {
            // Fallback to file
            try {
                log.debug("Loading config from file: {}", keycloakConfigFile);
                return new FileInputStream(keycloakConfigFile);
            } catch (FileNotFoundException fnfe) {
                String message = "Config not found for file: " + keycloakConfigFile;
                log.error(message);
                throw new RuntimeException(message, fnfe);
            }
        }
    }

    public void setKeycloakAuthenticatorProvider(KeycloakAuthenticatorProvider keycloakAuthenticatorProvider) {
        this.keycloakAuthenticatorProvider = keycloakAuthenticatorProvider;
    }
}
