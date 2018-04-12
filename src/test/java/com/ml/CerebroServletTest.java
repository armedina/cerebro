package com.ml;


import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.*;

import static com.ml.properties.Properties.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CerebroServletTest {
    @Mock
    Configuration config = HBaseConfiguration.create();
    @Mock
    Connection connection = ConnectionFactory.createConnection(config);
    @Mock
    private Table table;
    @Mock
    private Result result = org.mockito.Mockito.mock(Result.class);
    private static final String FAKE_URL = "fake.fk/hello";
    private final LocalServiceTestHelper helper = new LocalServiceTestHelper();
    @Mock
    private HttpServletRequest mockRequest;
    private MockHttpServletResponse mockHttpServletResponse= new MockHttpServletResponse();
    @Mock
    private MockHttpServletResponse mockResponse = new MockHttpServletResponse();
    @Mock
    private ServletContext mockServletContext;
    private StringWriter responseWriter;
    private CerebroServlet servletUnderTest;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        helper.setUp();

        when(mockRequest.getRequestURI()).thenReturn(FAKE_URL);

        when(mockRequest.getServletContext()).thenReturn(mockServletContext);

        when(mockServletContext.getAttribute(CONNECTION_ATTRIBUTE_NAME)).thenReturn(connection);
        when(connection.getTable(TableName.valueOf(TABLE_NAME))).thenReturn(table);

        // Set up a fake HTTP response.
        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseWriter));

        servletUnderTest = new CerebroServlet();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }

    public CerebroServletTest() throws IOException {
    }

    @Test
    public void doGet(){
        try {

            servletUnderTest.doGet(mockRequest,mockHttpServletResponse);
            assertEquals(500, mockHttpServletResponse.getStatus());

            Get get = new Get(HUMAN_COUNTER_ROW);
            when(table.get(get)).thenReturn(result);
            when(result.getValue(COLUMN_FAMILY_NAME,
                    COUNTER_COLUMN_NAME)).thenReturn(new byte[]{0,0,0,0,0,0,0,100});
            when(result.size()).thenReturn(1);

            get = new Get(MUTANT_COUNTER_ROW);
            result = org.mockito.Mockito.mock(Result.class);
            when(table.get(get)).thenReturn(result);
            when(result.getValue(COLUMN_FAMILY_NAME,
                    COUNTER_COLUMN_NAME)).thenReturn(new byte[]{0,0,0,0,0,0,0,50});
            when(result.size()).thenReturn(1);

            servletUnderTest.doGet(mockRequest,mockResponse);

            assertEquals("{\"count_mutant_dna\":50,\"count_human_dna\":100,\"ratio\":0.5}\n",
                    responseWriter.toString());

        } catch (ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    @Test
    public void doPost(){
        try {

            when(table.get(any(Get.class))).thenReturn(result);
            when(result.size()).thenReturn(0);

            Reader reader = new StringReader("{\"dna\":[\"ATGCGA\",\"CAGTGC\",\"TTATTT\",\"AGACGG\",\"GCGTCA\",\"TCACTG\"]}");
            BufferedReader bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(403,mockHttpServletResponse.getStatus());

            reader = new StringReader("{\"dna\":[\"ATGCGA\",\"CAGTGC\",\"TTATGT\",\"AGAAGG\",\"CCCCTA\",\"TCACTG\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(200,mockHttpServletResponse.getStatus());


            when(table.get(any(Get.class))).thenReturn(result);
            when(result.size()).thenReturn(1);
            when(result.getValue(COLUMN_FAMILY_NAME,
                    COUNTER_COLUMN_NAME)).thenReturn(new byte[]{0,0,0,0,0,0,0,0});

            reader = new StringReader("{\"dna\":[\"ATGCGA\",\"CAGTGC\",\"TTATTT\",\"AGACGG\",\"GCGTCA\",\"TCACTG\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(403,mockHttpServletResponse.getStatus());

            reader = new StringReader("{\"dna\":[\"ATgcGA\",\"CAGTGC\",\"TTATTT\",\"AGACGG\",\"GCGTCA\",\"TCACTG\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(400, mockHttpServletResponse.getStatus());
            assertEquals("WRONG DNA SEQUENCE:wrong letters",mockHttpServletResponse.getMessage());

            reader = new StringReader("{\"dna\":[\"ATLLGA\",\"CAGTGC\",\"TTATTT\",\"AGACGG\",\"GCGTCA\",\"TCACTG\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(400, mockHttpServletResponse.getStatus());
            assertEquals("WRONG DNA SEQUENCE:wrong letters",mockHttpServletResponse.getMessage());

            reader = new StringReader("\"dna\":[\"ATGCGA\",\"CAGTGC\",\"TTATTT\",\"AGACGG\",\"GCGTCA\",\"TCACTG\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(400, mockHttpServletResponse.getStatus());
            assertEquals("WRONG JSON FORMAT:syntax error",mockHttpServletResponse.getMessage());

            reader = new StringReader("{\"adn\":[\"ATGCGA\",\"CAGTGC\",\"TTATTT\",\"AGACGG\",\"GCGTCA\",\"TCACTG\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(400,mockHttpServletResponse.getStatus());
            assertEquals("WRONG JSON FORMAT:dna key missed",mockHttpServletResponse.getMessage());

            reader = new StringReader("{\"dna\":[\"ATGCGA\",\"CAGTGC\",\"TTATTT\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(400,mockHttpServletResponse.getStatus());
            assertEquals("WRONG DNA SEQUENCE:wrong length of columns",mockHttpServletResponse.getMessage());

            reader = new StringReader("{\"dna\":[\"ATGCGA\",\"CAGTGC\",\"TTATTT\",\"AGACGG\",\"GCGTCA\",\"TCACT\"]}");
            bufferedReader = new BufferedReader(reader);
            when(mockRequest.getReader()).thenReturn(bufferedReader);
            servletUnderTest.doPost(mockRequest,mockHttpServletResponse);
            assertEquals(400,mockHttpServletResponse.getStatus());
            assertEquals("WRONG DNA SEQUENCE:wrong length of row",mockHttpServletResponse.getMessage());


        } catch (ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
