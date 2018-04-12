package com.ml;

import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;


@RunWith(JUnit4.class)
public class IsMutantTest {

    private final LocalServiceTestHelper helper = new LocalServiceTestHelper();
    private CerebroServlet servletUnderTest;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        helper.setUp();
        servletUnderTest = new CerebroServlet();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }


    @Test
    public void isMutantTest(){
        String[] dna = {"ATGCGA","CAGTGC","TTATTT","AGACGG","GCGTCA","TCACTG"};
        boolean result = servletUnderTest.isMutant(dna);
        assertThat(result).isFalse();

        dna = new String[]{"ATGCGA","CAGTGC","TTATGT","AGAAGG","CCCCTA","TCACTG"};
        result = servletUnderTest.isMutant(dna);
        assertThat(result).isTrue();
    }

}
