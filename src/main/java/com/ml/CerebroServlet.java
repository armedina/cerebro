package com.ml;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;

import static com.ml.properties.Properties.*;

/**
 * This servlet support post request with json body, i.e:
 *
 * {"dna":["ATGCAA","CAGACC","TTATGT","AAGGAG","CTCCTA","TTTTTT"]}
 *
 * Also support GET request, and respond with
 *
 * {"count_mutant_dna":0,"count_human_dna":0,"ratio":0.0}
 *
 *
 * @author Andres Medina
 */

@WebServlet(urlPatterns = {"/mutant/", "/stats"})
public class CerebroServlet extends HttpServlet {

    private static final String DNA_KEY = "dna";
    private static final short HORIZONTAL_INCREMENT = 64;
    private static final short VERTICAL_INCREMENT = 16;
    private static final short LEFT_DIAGONAL_INCREMENT = 4;
    private static final short RIGHT_DIAGONAL_INCREMENT = 1;
    private static final short HORIZONTAL_SEQUENCE = 192;
    private static final short VERTICAL_SEQUENCE = 48;
    private static final short LEFT_DIAGONAL_SEQUENCE = 12;
    private static final short RIGHT_DIAGONAL_SEQUENCE = 3;


    /**
     * If the request dont have a json o body or a
     * valid json i.e. :
     * Only A,T,C,G letters
     * Only uppercase
     * Must contain dna key
     * dna key must be a Json Array with string, and each string
     * must have same length that Json Array Length
     *
     * If body don't accopmlish this requirements the response will
     * be a HTTP CODE 400
     *
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        ServletContext context = req.getServletContext();
        Connection connection = (Connection) context.getAttribute(CONNECTION_ATTRIBUTE_NAME);

        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        JsonParser jsonParser = new JsonParser();
        JsonObject dnaSequence;


        try {
            dnaSequence =  jsonParser.parse(req.getReader()).getAsJsonObject();
        } catch (JsonSyntaxException ex){
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,"WRONG JSON FORMAT:syntax error");
            return;
        }


        if(dnaSequence.has(DNA_KEY)) {

            JsonArray arraySequence = dnaSequence.getAsJsonArray(DNA_KEY);
            if(arraySequence != null) {
                int n = arraySequence.size();

                if(n < 4) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST,"WRONG DNA SEQUENCE:wrong length of columns");
                    return;
                }

                String[] dna = new String[n];
                for (int i = 0; i < arraySequence.size(); i++) {
                    String row = arraySequence.get(i).getAsString();
                    if( row.length() == n) {
                        if(row.matches("^[A,T,G,C]*$")) {
                            dna[i] = row;
                        } else {
                            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,"WRONG DNA SEQUENCE:wrong letters");
                            return;
                        }
                    } else {
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST,"WRONG DNA SEQUENCE:wrong length of row");
                        return;
                    }
                }

                String dnaSequenceString = dnaSequence.toString();
                try (Table table = connection.getTable(TableName.valueOf(TABLE_NAME))) {
                    MessageDigest messageDigest = MessageDigest.getInstance("md5");
                    byte[] hash = messageDigest.digest(dnaSequenceString.getBytes());

                    //Search sequence, if exist will not be stored, and
                    //the result if correspond to a mutant will be obtained
                    //from db
                    Boolean exist = verifyDNA(table, hash);

                    if(exist != null) {
                        if(exist.booleanValue() == true) {
                            resp.setStatus(HttpServletResponse.SC_OK);
                        }
                    } else {
                        if (isMutant(dna)) {
                            persist(table, hash, dnaSequenceString, true);
                            resp.setStatus(HttpServletResponse.SC_OK);
                        } else {
                            persist(table, hash, dnaSequenceString, false);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }

        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,"WRONG JSON FORMAT:dna key missed");
        }

    }


    /**
     * Return stats of DNA sequence analyzed and how many of each type exist
     * If occurs some error return HTTP CODE 500
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        StringBuilder result = new StringBuilder();

        ServletContext context = req.getServletContext();
        Connection connection = (Connection) context.getAttribute(CONNECTION_ATTRIBUTE_NAME);

        try (Table table = connection.getTable(TableName.valueOf(TABLE_NAME))) {

            Get get = new Get(MUTANT_COUNTER_ROW);
            Result getResult = table.get(get);
            long mutants = 0;
            if(getResult.size() > 0) {
                mutants = Bytes.toLong(getResult.getValue(COLUMN_FAMILY_NAME, COUNTER_COLUMN_NAME));
            }

            get = new Get(HUMAN_COUNTER_ROW);
            getResult = table.get(get);

            long humans = 0;
            if(getResult.size() > 0) {
                humans = Bytes.toLong(getResult.getValue(COLUMN_FAMILY_NAME, COUNTER_COLUMN_NAME));
            }

            double ratio = 0;
            if(humans > 0) {
                ratio = mutants*1.0/humans*1.0;
            }

            result.append("{\"count_mutant_dna\":").append(mutants);
            result.append(",\"count_human_dna\":").append(humans);
            result.append(",\"ratio\":").append(ratio).append("}");

            String content = result.toString();
            resp.setContentType("application/json");
            resp.setContentLength(content.length());
            resp.getWriter().println(content);

        } catch (Exception ex){
            ex.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * Analyze DNA chains in search of a 4 letter sequence
     * (horizontal, vertical o diagonal), if found more than
     * one returns true, false in other case
     *
     * @param dna
     * @return true if found more than one sequence
     *         false in other case
     */
    public boolean isMutant(String[] dna) {
        int n = dna.length;
        int sequenceFounded = 0;
        short[] previousAccumulators = null;
        short[] currentAccumulators;


        char[] previousRow = null;
        char[] currentRow;
        for (int y = 0; y < n; y++) {
            currentRow = dna[y].toCharArray();
            currentAccumulators = new short[dna.length];
            for (int x = 0; x < n; x++) {
                //Horizontal search
                if(x != 0){
                    if(currentRow[x] == currentRow[x-1]) {
                        sequenceFounded += incrementAccumulator(currentAccumulators,x,
                                currentAccumulators[x - 1], HORIZONTAL_INCREMENT, HORIZONTAL_SEQUENCE);
                    }
                }

                if(previousRow != null) {
                    //Vertical search
                    if(currentRow[x] == previousRow[x]) {
                        sequenceFounded += incrementAccumulator(currentAccumulators,x,
                                previousAccumulators[x], VERTICAL_INCREMENT, VERTICAL_SEQUENCE);
                    }

                    //Left Diagonal search
                    if(x != 0 && currentRow[x] == previousRow[x-1]) {
                        sequenceFounded += incrementAccumulator(currentAccumulators,x,
                                previousAccumulators[x-1], LEFT_DIAGONAL_INCREMENT, LEFT_DIAGONAL_SEQUENCE);
                    }

                    //Wright Diagonal search
                    if(x < n -1 && currentRow[x] == previousRow[x+1]) {
                        sequenceFounded += incrementAccumulator(currentAccumulators,x,
                                previousAccumulators[x+1], RIGHT_DIAGONAL_INCREMENT, RIGHT_DIAGONAL_SEQUENCE);
                    }
                }

                if(sequenceFounded > 1){
                    return true;
                }

            }
            previousRow = currentRow;
            previousAccumulators = currentAccumulators;
        }
        return false;
    }


    /**
     * Increments in 1, accumulators used for detection of DNA sequences
     * and returns 1 if found a complete sequence (4 Letters) or 0 in other
     * cases
     *
     * @param vector
     * @param x
     * @param previousValue
     * @param increment
     * @param sequence
     * @return 1 if found a complete sequence
     *         0 in other cases
     */
    public int incrementAccumulator(short[] vector,int x, short previousValue, short increment, short sequence) {
        vector[x] = (short) (vector[x] | ((short)(previousValue + increment)));
        return ((vector[x] & sequence) == sequence) ? 1 : 0;
    }


    /**
     * Search a sequence in the DB
     *
     * @param table
     * @param rowKey
     * @return true if sequence exist in db and is from a mutant
     *         false if sequence exist in db and is from a human
     *         null if sequence not exist in db
     */
    private Boolean verifyDNA(Table table, byte[] rowKey){
        Boolean result = null;
        Get get = new Get(rowKey);
        try {
            Result getResult = table.get(get);
            if(getResult.size() > 0) {
                result = Bytes.toBoolean(getResult.getValue(COLUMN_FAMILY_NAME, IS_MUTANT_COLUMN_NAME));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Insert a row in db with sequence dna info, and increments
     * counter for humans and mutant detected
     *
     * @param table
     * @param rowKey
     * @param dnaChain
     * @param isMutant
     * @throws IOException
     */
    private void persist(Table table, byte[] rowKey, String dnaChain, boolean isMutant) throws IOException {
        Put put = new Put(rowKey);
        put.addColumn(COLUMN_FAMILY_NAME, CHAIN_COLUMN_NAME, Bytes.toBytes(dnaChain));
        put.addColumn(COLUMN_FAMILY_NAME, IS_MUTANT_COLUMN_NAME, Bytes.toBytes(isMutant));
        if (table.checkAndPut(rowKey,COLUMN_FAMILY_NAME,CHAIN_COLUMN_NAME,null,put)) {
            table.incrementColumnValue(isMutant ? MUTANT_COUNTER_ROW : HUMAN_COUNTER_ROW ,COLUMN_FAMILY_NAME, COUNTER_COLUMN_NAME,1l);
        }
    }
}
