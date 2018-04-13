package com.ml.io;

import com.google.cloud.bigtable.hbase.BigtableConfiguration;
import com.ml.properties.Properties;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.util.Bytes;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.IOException;

import static com.ml.properties.Properties.*;

/**
 * This listener get a connection db and share against servlet context,
 * so only one connection ist used in the same server instance
 *
 * @author Andres Medina
 */
@WebListener
public class BigTableConnector implements ServletContextListener {
    private static String PROJECT_ID;
    private static String INSTANCE_ID;
    private final static String APP_ENGINE_PROJECT_PROPERTY ="projectID";
    private final static String BIGTABLE_INSTANCE_PROPERTY ="instanceID";

    private static Connection connection = null;

    private static ServletContext sc;

    /** Connect will establish the connection to Cloud Bigtable. */
    public static void connect() throws IOException {

        connection = BigtableConfiguration.connect(PROJECT_ID, INSTANCE_ID);
    }

    /**
     * Get the shared connection to Cloud Bigtable.
     * @return the connection
     */
    public static Connection getConnection() {
        if (connection == null) {
            try {
                connect();
            } catch (IOException e) {
                if (sc != null) {
                    sc.log("connect ", e);
                }
            }
        }
        if (connection == null) {
            if (sc != null) {
                sc.log("BigtableConnector-No Connection");
            }
        }
        return connection;
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {

        PROJECT_ID = System.getProperty(APP_ENGINE_PROJECT_PROPERTY);
        INSTANCE_ID = System.getProperty(BIGTABLE_INSTANCE_PROPERTY);

        if (event != null) {
            sc = event.getServletContext();
        }

        try {
            connect();
        } catch (IOException e) {
            if (sc != null) {
                sc.log("BigtableConnector - connect ", e);
            }
        }
        if (connection == null) {
            if (sc != null) {
                sc.log("BigtableConnector-No Connection");
            }
        }
        if (sc != null) {
            sc.log("ctx Initialized: " + PROJECT_ID + " " + INSTANCE_ID);
        }
        create(connection);
        sc.setAttribute(Properties.CONNECTION_ATTRIBUTE_NAME,connection);
    }


    public static String create(Connection connection) {
        try {

            Admin admin = connection.getAdmin();

            HTableDescriptor descriptor = new HTableDescriptor(TableName.valueOf(TABLE_NAME));
            descriptor.addFamily(new HColumnDescriptor(COLUMN_FAMILY_NAME));
            admin.createTable(descriptor);

        } catch (IOException e) {
            return "Table exists.";
        }
        return "Create table " + Bytes.toString(TABLE_NAME);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (IOException io) {
            if (sc != null) {
                sc.log("contextDestroyed ", io);
            }
        }
        connection = null;
    }
}
