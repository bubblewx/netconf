/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.restconf.Draft18.MonitoringModule;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlDocumentUtils;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Abstract class for processing and preparing data
 *
 */
abstract class AbstractNotificationsData {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNotificationsData.class);

    private TransactionChainHandler transactionChainHandler;
    private SchemaContextHandler schemaHandler;
    private String localName;

    /**
     * Transaction chain for delete data in DS on close()
     *
     * @param transactionChainHandler
     *            - creating new write transaction for delete data on close
     * @param schemaHandler
     *            - for getting schema to deserialize
     *            {@link MonitoringModule#PATH_TO_STREAM_WITHOUT_KEY} to
     *            {@link YangInstanceIdentifier}
     */
    public void setCloseVars(final TransactionChainHandler transactionChainHandler,
            final SchemaContextHandler schemaHandler) {
        this.transactionChainHandler = transactionChainHandler;
        this.schemaHandler = schemaHandler;
    }

    /**
     * Delete data in DS
     */
    protected void deleteDataInDS() throws Exception {
        final DOMDataWriteTransaction wTx = this.transactionChainHandler.get().newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.OPERATIONAL, IdentifierCodec
                .deserialize(MonitoringModule.PATH_TO_STREAM_WITHOUT_KEY + this.localName, this.schemaHandler.get()));
        wTx.submit().checkedGet();
    }

    /**
     * Set localName of last path element of specific listener
     *
     * @param localName
     *            - local name
     */
    protected void setLocalNameOfPath(final String localName) {
        this.localName = localName;
    }

    /**
     * Formats data specified by RFC3339.
     *
     * @param d
     *            Date
     * @return Data specified by RFC3339.
     */
    protected static String toRFC3339(final Date d) {
        return ListenersConstants.RFC3339_PATTERN.matcher(ListenersConstants.RFC3339.format(d)).replaceAll("$1:$2");
    }

    /**
     * Creates {@link Document} document.
     *
     * @return {@link Document} document.
     */
    protected static Document createDocument() {
        final DocumentBuilder bob;
        try {
            bob = ListenersConstants.DBF.newDocumentBuilder();
        } catch (final ParserConfigurationException e) {
            return null;
        }
        return bob.newDocument();
    }

    /**
     * Write normalized node to {@link DOMResult}
     *
     * @param normalized
     *            - data
     * @param context
     *            - actual schema context
     * @param schemaPath
     *            - schema path of data
     * @return {@link DOMResult}
     */
    protected DOMResult writeNormalizedNode(final NormalizedNode<?, ?> normalized, final SchemaContext context,
            final SchemaPath schemaPath) throws IOException, XMLStreamException {
        final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newFactory();
        final Document doc = XmlDocumentUtils.getDocument();
        final DOMResult result = new DOMResult(doc);
        NormalizedNodeWriter normalizedNodeWriter = null;
        NormalizedNodeStreamWriter normalizedNodeStreamWriter = null;
        XMLStreamWriter writer = null;

        try {
            writer = XML_FACTORY.createXMLStreamWriter(result);
            normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
            normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter);

            normalizedNodeWriter.write(normalized);

            normalizedNodeWriter.flush();
        } finally {
            if (normalizedNodeWriter != null) {
                normalizedNodeWriter.close();
            }
            if (normalizedNodeStreamWriter != null) {
                normalizedNodeStreamWriter.close();
            }
            if (writer != null) {
                writer.close();
            }
        }

        return result;
    }

    /**
     * Generating base element of every notification
     *
     * @param doc
     *            - base {@link Document}
     * @return element of {@link Document}
     */
    protected Element basePartDoc(final Document doc) {
        final Element notificationElement =
                doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0", "notification");

        doc.appendChild(notificationElement);

        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(toRFC3339(new Date()));
        notificationElement.appendChild(eventTimeElement);

        return notificationElement;
    }

    /**
     * Generating of {@link Document} transforming to string
     *
     * @param doc
     *            - {@link Document} with data
     * @return - string from {@link Document}
     */
    protected String transformDoc(final Document doc) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Transformer transformer = ListenersConstants.FACTORY.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc),
                    new StreamResult(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
            final byte[] charData = out.toByteArray();
            return new String(charData, "UTF-8");
        } catch (TransformerException | UnsupportedEncodingException e) {
            final String msg = "Error during transformation of Document into String";
            LOG.error(msg, e);
            return msg;
        }
    }
}
