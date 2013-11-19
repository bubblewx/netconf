package org.opendaylight.controller.sal.restconf.impl.test;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.sal.rest.api.RestconfService;
import org.opendaylight.controller.sal.rest.impl.StructuredDataToXmlProvider;
import org.opendaylight.controller.sal.rest.impl.XmlToCompositeNodeProvider;
import org.opendaylight.controller.sal.restconf.impl.BrokerFacade;
import org.opendaylight.controller.sal.restconf.impl.ControllerContext;
import org.opendaylight.controller.sal.restconf.impl.MediaTypes;
import org.opendaylight.controller.sal.restconf.impl.RestconfImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.CompositeNode;
import org.opendaylight.yangtools.yang.data.api.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.common.base.Charsets;

public class XmlProvidersTest extends JerseyTest {

    private static ControllerContext controllerContext;
    private static BrokerFacade brokerFacade;
    private static RestconfImpl restconfImpl;

    @BeforeClass
    public static void init() {
        Set<Module> allModules = null;
        try {
            allModules = TestUtils.loadModules(RestconfImplTest.class.getResource("/full-versions/yangs").getPath());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        SchemaContext schemaContext = TestUtils.loadSchemaContext(allModules);
        controllerContext = ControllerContext.getInstance();
        controllerContext.setSchemas(schemaContext);
        brokerFacade = mock(BrokerFacade.class);
        restconfImpl = RestconfImpl.getInstance();
        restconfImpl.setBroker(brokerFacade);
        restconfImpl.setControllerContext(controllerContext);
    }

    @Before
    public void logs() {
        List<LogRecord> loggedRecords = getLoggedRecords();
        for (LogRecord l : loggedRecords) {
            System.out.println(l.getMessage());
        }
    }

    @Test
    public void testStructuredDataToXmlProvider() throws FileNotFoundException {
        URI uri = null;
        try {
            uri = new URI("/datastore/" + URLEncoder.encode("ietf-interfaces:interfaces/interface/eth0", Charsets.US_ASCII.name()).toString());
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            e.printStackTrace();
        }
        
        InputStream xmlStream = RestconfImplTest.class.getResourceAsStream("/parts/ietf-interfaces_interfaces.xml");
        CompositeNode loadedCompositeNode = TestUtils.loadCompositeNode(xmlStream);
        when(brokerFacade.readOperationalData(any(InstanceIdentifier.class))).thenReturn(loadedCompositeNode);
        
        Response response = target(uri.toASCIIString()).request(MediaTypes.API+RestconfService.XML).get();
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testXmlToCompositeNodeProvider() throws ParserConfigurationException, SAXException, IOException {
        URI uri = null;
        try {
            uri = new URI("/config/" + URLEncoder.encode("ietf-interfaces:interfaces/interface/eth0", Charsets.US_ASCII.name()).toString());
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            e.printStackTrace();
        }
        InputStream xmlStream = RestconfImplTest.class.getResourceAsStream("/parts/ietf-interfaces_interfaces.xml");
        final CompositeNode loadedCompositeNode = TestUtils.loadCompositeNode(xmlStream);
        when(brokerFacade.commitConfigurationDataPut(any(InstanceIdentifier.class), any(CompositeNode.class))).thenReturn(new Future<RpcResult<TransactionStatus>>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
            @Override
            public boolean isCancelled() {
                return false;
            }
            @Override
            public boolean isDone() {
                return false;
            }
            @Override
            public RpcResult<TransactionStatus> get() throws InterruptedException, ExecutionException {
                return null;
            }
            @Override
            public RpcResult<TransactionStatus> get(long timeout, TimeUnit unit) throws InterruptedException,
                    ExecutionException, TimeoutException {
                return null;
            }
        });
        
        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        xmlStream = RestconfImplTest.class.getResourceAsStream("/parts/ietf-interfaces_interfaces.xml");
        Document doc = docBuilder.parse(xmlStream);
        
        Response response = target(uri.toASCIIString()).request(MediaTypes.API+RestconfService.XML).post(Entity.entity(TestUtils.getDocumentInPrintableForm(doc), new MediaType("application","vnd.yang.api+xml")));
        assertEquals(204, response.getStatus());
    }
    
    @Test
    public void testXmlToCompositeNodeProviderExceptions() {
        URI uri = null;
        try {
            uri = new URI("/operations/" + URLEncoder.encode("ietf-interfaces:interfaces/interface/eth0", Charsets.US_ASCII.name()).toString());
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            e.printStackTrace();
        }
        
        Response response = target(uri.toASCIIString()).request(MediaTypes.API + RestconfService.XML).post(
                Entity.entity("<SimpleNode/>", new MediaType("application", "vnd.yang.api+xml")));
        assertEquals(400, response.getStatus());
        
        response = target(uri.toASCIIString()).request(MediaTypes.API + RestconfService.XML).post(
                Entity.entity("<SimpleNode>", new MediaType("application", "vnd.yang.api+xml")));
        assertEquals(400, response.getStatus());
    }
    
    @Test
    public void testXmlToCompositeNode404NotFound() {
        URI uri = null;
        try {
            uri = new URI("/datastore/" + URLEncoder.encode("ietf-interfaces:interfaces/interface/eth0", Charsets.US_ASCII.name()).toString());
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            e.printStackTrace();
        }
        
        when(brokerFacade.readOperationalData(any(InstanceIdentifier.class))).thenReturn(null);
        
        Response response = target(uri.toASCIIString()).request(MediaTypes.API+RestconfService.XML).get();
        assertEquals(404, response.getStatus());
    }

    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);
        enable(TestProperties.RECORD_LOG_LEVEL);
        set(TestProperties.RECORD_LOG_LEVEL, Level.ALL.intValue());
        
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig = resourceConfig.registerInstances(restconfImpl, StructuredDataToXmlProvider.INSTANCE, XmlToCompositeNodeProvider.INSTANCE);
        return resourceConfig;
    }

}