/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard <grenard@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core.operations.document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

/**
 * @since 10.1
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.automation.core")
public class OrderDocumentTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService service;

    protected DocumentModel oFolder1;

    protected DocumentModel folder2;

    protected DocumentModel oFolder3;

    protected DocumentModel file11;

    protected DocumentModel file12;

    protected DocumentModel file13;

    protected DocumentModel file14;

    protected DocumentModel file15;

    protected DocumentModel file21;

    protected DocumentModel file22;

    protected DocumentModel file31;

    protected DocumentModel file32;

    protected OperationContext ctx;

    @Before
    public void initRepo() {

        oFolder1 = session.createDocumentModel("/", "ofolder1", "OrderedFolder");
        oFolder1.setPropertyValue("dc:title", "Ordered Folder1"); // NOSONAR
        oFolder1 = session.createDocument(oFolder1);

        file11 = session.createDocumentModel("/ofolder1", "File11", "File"); // NOSONAR
        file11.setPropertyValue("dc:title", "File11");
        file11 = session.createDocument(file11);

        file12 = session.createDocumentModel("/ofolder1", "File12", "File");
        file12.setPropertyValue("dc:title", "File12");
        file12 = session.createDocument(file12);

        file13 = session.createDocumentModel("/ofolder1", "File13", "File");
        file13.setPropertyValue("dc:title", "File13");
        file13 = session.createDocument(file13);

        file14 = session.createDocumentModel("/ofolder1", "File14", "File");
        file14.setPropertyValue("dc:title", "File14");
        file14 = session.createDocument(file14);

        file15 = session.createDocumentModel("/ofolder1", "File15", "File");
        file15.setPropertyValue("dc:title", "File15");
        file15 = session.createDocument(file15);

        folder2 = session.createDocumentModel("/", "folder2", "Folder");
        folder2.setPropertyValue("dc:title", "Normal Folder 2");
        folder2 = session.createDocument(folder2);

        file21 = session.createDocumentModel("/folder2", "File21", "File");
        file21.setPropertyValue("dc:title", "File21");
        file21 = session.createDocument(file21);

        file22 = session.createDocumentModel("/folder2", "File22", "File");
        file22.setPropertyValue("dc:title", "File22");
        file22 = session.createDocument(file22);

        oFolder3 = session.createDocumentModel("/", "ofolder3", "OrderedFolder");
        oFolder3 = session.createDocument(oFolder3);

        file31 = session.createDocumentModel("/ofolder3", "File31", "File");
        file31 = session.createDocument(file31);

        file32 = session.createDocumentModel("/ofolder3", "File32", "File");
        file32 = session.createDocument(file32);

        session.save();
        ctx = new OperationContext(session);
    }

    @After
    public void closeOperationContext() {
        ctx.close();
    }

    @Test
    public void testIllegalOrderDocument() {
        var e = assertThrows("Should not be able to order in different folder", NuxeoException.class,
                () -> moveBefore(file12, file21));
        assertTrue(e.getMessage().contains(OrderDocument.NOT_SAME_FOLDER_ERROR_MSG));
    }

    @Test
    public void testOrderDocument() throws OperationException {
        assertEquals(Long.valueOf(0), file11.getPos());
        assertEquals(Long.valueOf(1), file12.getPos());
        assertEquals(Long.valueOf(2), file13.getPos());
        assertEquals(Long.valueOf(3), file14.getPos());
        assertEquals(Long.valueOf(4), file15.getPos());
        assertNull(file21.getPos());
        assertNull(file22.getPos());
        assertEquals(Long.valueOf(0), file31.getPos());
        assertEquals(Long.valueOf(1), file32.getPos());

        // Effective
        moveBefore(file12, file11);

        assertEquals(Long.valueOf(1), file11.getPos());
        assertEquals(Long.valueOf(0), file12.getPos());
        assertEquals(Long.valueOf(2), file13.getPos());
        assertEquals(Long.valueOf(3), file14.getPos());
        assertEquals(Long.valueOf(4), file15.getPos());

        // Ineffective i.e. unchanged
        moveBefore(file11, file13);

        assertEquals(Long.valueOf(1), file11.getPos());
        assertEquals(Long.valueOf(0), file12.getPos());
        assertEquals(Long.valueOf(2), file13.getPos());
        assertEquals(Long.valueOf(3), file14.getPos());
        assertEquals(Long.valueOf(4), file15.getPos());

        // Effective
        moveBefore(file13, file11);

        assertEquals(Long.valueOf(2), file11.getPos());
        assertEquals(Long.valueOf(0), file12.getPos());
        assertEquals(Long.valueOf(1), file13.getPos());
        assertEquals(Long.valueOf(3), file14.getPos());
        assertEquals(Long.valueOf(4), file15.getPos());

        // Move last
        moveBefore(file12, null);

        assertEquals(Long.valueOf(1), file11.getPos());
        assertEquals(Long.valueOf(4), file12.getPos());
        assertEquals(Long.valueOf(0), file13.getPos());
        assertEquals(Long.valueOf(2), file14.getPos());
        assertEquals(Long.valueOf(3), file15.getPos());
    }

    @Test
    public void testOrderDocumentMultipleSimple() throws OperationException {
        assertEquals(Long.valueOf(0), file11.getPos());
        assertEquals(Long.valueOf(1), file12.getPos());
        assertEquals(Long.valueOf(2), file13.getPos());

        // Effective
        moveBeforeMultiple(new DocumentModel[] { file12, file13 }, file11);

        assertEquals(Long.valueOf(2), file11.getPos());
        assertEquals(Long.valueOf(0), file12.getPos());
        assertEquals(Long.valueOf(1), file13.getPos());

        // Ineffective
        moveBeforeMultiple(new DocumentModel[] { file12, file13 }, file11);

        assertEquals(Long.valueOf(2), file11.getPos());
        assertEquals(Long.valueOf(0), file12.getPos());
        assertEquals(Long.valueOf(1), file13.getPos());
    }

    @Test
    public void testOrderDocumentMultipleComlex() throws OperationException {
        assertEquals(Long.valueOf(0), file11.getPos());
        assertEquals(Long.valueOf(1), file12.getPos());
        assertEquals(Long.valueOf(2), file13.getPos());
        assertEquals(Long.valueOf(3), file14.getPos());
        assertEquals(Long.valueOf(4), file15.getPos());

        // Effective
        moveBeforeMultiple(new DocumentModel[] { file13, file15 }, file12);

        assertEquals(Long.valueOf(0), file11.getPos());
        assertEquals(Long.valueOf(3), file12.getPos());
        assertEquals(Long.valueOf(1), file13.getPos());
        assertEquals(Long.valueOf(4), file14.getPos());
        assertEquals(Long.valueOf(2), file15.getPos());
    }

    @Test
    public void testOrderDocumentMultipleLast() throws OperationException {
        assertEquals(Long.valueOf(0), file11.getPos());
        assertEquals(Long.valueOf(1), file12.getPos());
        assertEquals(Long.valueOf(2), file13.getPos());
        assertEquals(Long.valueOf(3), file14.getPos());
        assertEquals(Long.valueOf(4), file15.getPos());

        // Effective
        moveBeforeMultiple(new DocumentModel[] { file13, file15 }, null);

        assertEquals(Long.valueOf(0), file11.getPos());
        assertEquals(Long.valueOf(1), file12.getPos());
        assertEquals(Long.valueOf(3), file13.getPos());
        assertEquals(Long.valueOf(2), file14.getPos());
        assertEquals(Long.valueOf(4), file15.getPos());
    }

    @Test
    public void testMoveIntoOrderedFolder() {
        assertEquals(Long.valueOf(4), file15.getPos());
        assertNull(file22.getPos());

        // Move a file from an unordered folder to an ordered folder
        session.move(file22.getRef(), oFolder1.getRef(), file22.getName());
        file22 = session.getDocument(file22.getRef());
        assertEquals(Long.valueOf(5), file22.getPos());

        // Move a file from an ordered to another ordered folder
        assertEquals(Long.valueOf(0), file31.getPos());
        assertEquals(Long.valueOf(1), file32.getPos());
        session.move(file31.getRef(), oFolder1.getRef(), file31.getName());
        file31 = session.getDocument(file31.getRef());
        assertEquals(Long.valueOf(6), file31.getPos());
        // expected, same case than after removal, no ecm:pos changes
        assertEquals(Long.valueOf(1), file32.getPos());
    }

    protected void moveBefore(DocumentModel src, DocumentModel dest) throws OperationException {
        ctx.setInput(src);
        Map<String, Object> params = new HashMap<>();
        if (dest != null) {
            params.put("before", dest);
        }
        service.run(ctx, OrderDocument.ID, params);
        file11 = session.getDocument(file11.getRef());
        file12 = session.getDocument(file12.getRef());
        file13 = session.getDocument(file13.getRef());
        file14 = session.getDocument(file14.getRef());
        file15 = session.getDocument(file15.getRef());
    }

    protected void moveBeforeMultiple(DocumentModel[] srcList, DocumentModel dest) throws OperationException {
        ctx.setInput(Arrays.asList(srcList));
        Map<String, Object> params = new HashMap<>();
        if (dest != null) {
            params.put("before", dest);
        }
        service.run(ctx, OrderDocument.ID, params);
        file11 = session.getDocument(file11.getRef());
        file12 = session.getDocument(file12.getRef());
        file13 = session.getDocument(file13.getRef());
        file14 = session.getDocument(file14.getRef());
        file15 = session.getDocument(file15.getRef());
    }

}
