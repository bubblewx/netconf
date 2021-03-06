/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for streams
 *
 * <ul>
 * <li>create stream
 * <li>subscribe
 * </ul>
 *
 */
public final class CreateStreamUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CreateStreamUtil.class);
    private static final String OUTPUT_TYPE_PARAM_NAME = "notification-output-type";

    private CreateStreamUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Create stream with POST operation via RPC
     *
     * @param payload
     *            - input of rpc - example in JSON:
     *
     *            <pre>
     *            {@code
     *            {
     *                "input": {
     *                    "path": "/toaster:toaster/toaster:toasterStatus",
     *                    "sal-remote-augment:datastore": "OPERATIONAL",
     *                    "sal-remote-augment:scope": "ONE"
     *                }
     *            }
     *            }
     *            </pre>
     *
     * @param refSchemaCtx
     *            - reference to {@link SchemaContext} -
     *            {@link SchemaContextRef}
     * @return {@link CheckedFuture} with {@link DOMRpcResult} - This mean
     *         output of RPC - example in JSON:
     *
     *         <pre>
     *         {@code
     *         {
     *             "output": {
     *                 "stream-name": "toaster:toaster/toaster:toasterStatus/datastore=OPERATIONAL/scope=ONE"
     *             }
     *         }
     *         }
     *         </pre>
     *
     */
    public static DOMRpcResult createDataChangeNotifiStream(final NormalizedNodeContext payload,
            final SchemaContextRef refSchemaCtx) {
        final ContainerNode data = (ContainerNode) payload.getData();
        final QName qname = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final YangInstanceIdentifier path = preparePath(data, qname);
        String streamName = prepareDataChangeNotifiStreamName(path, refSchemaCtx.get(), data);

        final QName outputQname = QName.create(qname, "output");
        final QName streamNameQname = QName.create(qname, "stream-name");

        final NotificationOutputType outputType = prepareOutputType(data);
        if(outputType.equals(NotificationOutputType.JSON)){
            streamName = streamName + "/JSON";
        }

        if (!Notificator.existListenerFor(streamName)) {
            Notificator.createListener(path, streamName, outputType);
        }

        final ContainerNode output =
                ImmutableContainerNodeBuilder.create().withNodeIdentifier(new NodeIdentifier(outputQname))
                        .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();
        return new DefaultDOMRpcResult(output);
    }

    /**
     * @param data
     *            - data of notification
     * @return output type fo notification
     */
    private static NotificationOutputType prepareOutputType(final ContainerNode data) {
        NotificationOutputType outputType = parseEnum(data, NotificationOutputType.class, OUTPUT_TYPE_PARAM_NAME);
        return outputType = outputType == null ? NotificationOutputType.XML : outputType;
    }

    private static String prepareDataChangeNotifiStreamName(final YangInstanceIdentifier path, final SchemaContext schemaContext,
            final ContainerNode data) {
        LogicalDatastoreType ds = parseEnum(data, LogicalDatastoreType.class,
                RestconfStreamsConstants.DATASTORE_PARAM_NAME);
        ds = ds == null ? RestconfStreamsConstants.DEFAULT_DS : ds;

        DataChangeScope scope = parseEnum(data, DataChangeScope.class, RestconfStreamsConstants.SCOPE_PARAM_NAME);
        scope = scope == null ? RestconfStreamsConstants.DEFAULT_SCOPE : scope;

        final String streamName = RestconfStreamsConstants.DATA_SUBSCR + "/"
                + Notificator
                .createStreamNameFromUri(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext)
                + RestconfStreamsConstants.DS_URI + ds + RestconfStreamsConstants.SCOPE_URI + scope);
        return streamName;
    }

    private static <T> T parseEnum(final ContainerNode data, final Class<T> clazz, final String paramName) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> augNode = data
                .getChild(RestconfStreamsConstants.SAL_REMOTE_AUG_IDENTIFIER);
        if (!augNode.isPresent() && !(augNode instanceof AugmentationNode)) {
            return null;
        }
        final Optional<DataContainerChild<? extends PathArgument, ?>> enumNode =
                ((AugmentationNode) augNode.get()).getChild(
                        new NodeIdentifier(QName.create(RestconfStreamsConstants.SAL_REMOTE_AUGMENT, paramName)));
        if (!enumNode.isPresent()) {
            return null;
        }
        final Object value = enumNode.get().getValue();
        if (!(value instanceof String)) {
            return null;
        }

        return ResolveEnumUtil.resolveEnum(clazz, (String) value);
    }

    private static YangInstanceIdentifier preparePath(final ContainerNode data, final QName qName) {
        final Optional<DataContainerChild<? extends PathArgument, ?>> path = data
                .getChild(new YangInstanceIdentifier.NodeIdentifier(QName.create(qName, "path")));
        Object pathValue = null;
        if (path.isPresent()) {
            pathValue = path.get().getValue();
        }
        if (!(pathValue instanceof YangInstanceIdentifier)) {
            final String errMsg = "Instance identifier was not normalized correctly ";
            LOG.debug(errMsg + qName);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        }
        return (YangInstanceIdentifier) pathValue;
    }

    /**
     * Create stream with POST operation via RPC
     *
     * @param payload
     *            - input of RPC
     * @param refSchemaCtx
     *            - schemaContext
     * @return {@link DOMRpcResult}
     */
    public static DOMRpcResult createYangNotifiStream(final NormalizedNodeContext payload,
            final SchemaContextRef refSchemaCtx) {
        final ContainerNode data = (ContainerNode) payload.getData();
        LeafSetNode leafSet = null;
        String outputType = "XML";
        for (final DataContainerChild<? extends PathArgument, ?> dataChild : data.getValue()) {
            if (dataChild instanceof LeafSetNode) {
                leafSet = (LeafSetNode) dataChild;
            } else if (dataChild instanceof AugmentationNode) {
                outputType = (String) (((AugmentationNode) dataChild).getValue()).iterator().next().getValue();
            }
        }

        final Collection<LeafSetEntryNode> entryNodes = leafSet.getValue();
        final List<SchemaPath> paths = new ArrayList<>();
        String streamName = RestconfStreamsConstants.CREATE_NOTIFICATION_STREAM + "/";

        final Iterator<LeafSetEntryNode> iterator = entryNodes.iterator();
        while (iterator.hasNext()) {
            final QName valueQName = QName.create((String) iterator.next().getValue());
            final Module module = refSchemaCtx.findModuleByNamespaceAndRevision(valueQName.getModule().getNamespace(),
                    valueQName.getModule().getRevision());
            Preconditions.checkNotNull(module,
                    "Module for namespace " + valueQName.getModule().getNamespace() + " does not exist");
            NotificationDefinition notifiDef = null;
            for (final NotificationDefinition notification : module.getNotifications()) {
                if (notification.getQName().equals(valueQName)) {
                    notifiDef = notification;
                    break;
                }
            }
            final String moduleName = module.getName();
            Preconditions.checkNotNull(notifiDef,
                    "Notification " + valueQName + "doesn't exist in module " + moduleName);
            paths.add(notifiDef.getPath());
            streamName = streamName + moduleName + ":" + valueQName.getLocalName();
            if (iterator.hasNext()) {
                streamName = streamName + ",";
            }
        }
        if (outputType.equals("JSON")) {
            streamName = streamName + "/JSON";
        }
        final QName rpcQName = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final QName outputQname = QName.create(rpcQName, "output");
        final QName streamNameQname = QName.create(rpcQName, "notification-stream-identifier");

        final ContainerNode output =
                ImmutableContainerNodeBuilder.create().withNodeIdentifier(new NodeIdentifier(outputQname))
                        .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();

        if (!Notificator.existNotificationListenerFor(streamName)) {
            Notificator.createNotificationListener(paths, streamName, outputType);
        }

        return new DefaultDOMRpcResult(output);
    }
}
