package com.ml.properties;

import org.apache.hadoop.hbase.util.Bytes;

/**
 * This class contains constants used in the app
 *
 * @author Andres Medina
 */
public class Properties {

    public static final String CONNECTION_ATTRIBUTE_NAME = "connection";
    public static final byte[] COUNTER_COLUMN_NAME = Bytes.toBytes("value");
    public static final byte[] CHAIN_COLUMN_NAME = Bytes.toBytes("chain");
    public static final byte[] IS_MUTANT_COLUMN_NAME = Bytes.toBytes("isMutant");
    public static final byte[] TABLE_NAME = Bytes.toBytes("dna");
    public static final byte[] COLUMN_FAMILY_NAME = Bytes.toBytes("cfc");
    public static final byte[] MUTANT_COUNTER_ROW = Bytes.toBytes("mutantCounter");
    public static final byte[] HUMAN_COUNTER_ROW = Bytes.toBytes("humanCounter");
}
