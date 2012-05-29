package com.zend.db;

/*
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.

    * Neither the name of Zend Technologies USA, Inc. nor the names of its
      contributors may be used to endorse or promote products derived from this
      software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.exception.CommandLineParsingException;
import liquibase.resource.ClassLoaderResourceAccessor;

import liquibase.diff.Diff;
import liquibase.diff.DiffResult;
import liquibase.diff.DiffStatusListener;
import liquibase.exception.DatabaseException;

import liquibase.integration.commandline.CommandLineUtils;
import liquibase.logging.LogFactory;
import liquibase.servicelocator.ServiceLocator;

/**
 *
 * @author Slavey Karadzhov <slavey@zend.com>
 */
public class Migration {

    private String driver; // "com.mysql.jdbc.Driver";
    private String url; //  "jdbc:mysql://localhost/liquibase";
    private String username; // "benutzername";
    private String password; // "passwort";
    private String changelog; // "changelog.xml";
    private Database databaseConnection;
    private Liquibase liquibase;

    protected ClassLoader classLoader;
    protected String defaultSchemaName;

    public Migration(String driver,
                     String host,
                     String database,
                     String username,
                     String password,
                     String changelog
                     ) throws Exception {
        String[] data = this.getDriverAndUrl(driver, host, database);

        this.driver = data[0];
        this.url = data[1];
        this.username = username;
        this.password = password;
        this.changelog = changelog;

        this.configureClassLoader();
        this.getConnection();
        this.liquibase = new Liquibase(this.changelog, new ClassLoaderResourceAccessor(), this.databaseConnection);
    }

    private String[] getDriverAndUrl(String driver, String host, String database) {
        String url = "";
        driver = driver.toLowerCase();
        if (driver.contains("mysql")) {
            driver = "com.mysql.jdbc.Driver";
            url = "jdbc:mysql://"+host+"/"+database;
        }

        String[] data = new String[2];
        data[0] = driver;
        data[1] = url;

        return data;
    }

    private void configureClassLoader() throws CommandLineParsingException {
        final List<URL> urls = new ArrayList<URL>();
        classLoader = AccessController.doPrivileged(new PrivilegedAction<URLClassLoader>() {

                public URLClassLoader run() {
                    return new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
                }
        });

        ServiceLocator.getInstance().setResourceAccessor(
                                    new ClassLoaderResourceAccessor(classLoader)
                                    );
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    private Database getConnection() throws Exception {
        if (this.databaseConnection == null) {
            this.databaseConnection = CommandLineUtils.createDatabaseObject(
                                                        classLoader,
                                                        this.url,
                                                        this.username,
                                                        this.password,
                                                        this.driver,
                                                        this.defaultSchemaName,
                                                        null, null
                                                        );
        }

        return this.databaseConnection;
    }

    /**
     * Gets the current database connection
     * @return Database
     */
    public Database getDatabase() {
        return this.liquibase.getDatabase();
    }

    public void update(String tag) throws Exception {
        this.liquibase.update(null);
        this.liquibase.tag(tag);

    }

    public void rollback(String tag)throws Exception {
        this.liquibase.rollback(tag, this.changelog);
    }

    /**
     * Generates changelog file between two tables
     *
     * @param driver
     * @param host
     * @param database
     * @param username
     * @param password
     * @throws Exception
     */
    public String diff(String driver,
                     String host,
                     String database,
                     String username,
                     String password
                     ) throws Exception {

        String[] data = this.getDriverAndUrl(driver, host, database);

        Database targetDatabaseConnection  = CommandLineUtils.createDatabaseObject(
                                                        classLoader,
                                                        data[1],
                                                        username,
                                                        password,
                                                        data[0],
                                                        this.defaultSchemaName,
                                                        null, null
                                                        );


        Diff diff = new Diff(this.databaseConnection, targetDatabaseConnection);
        diff.addStatusListener(new OutDiffStatusListener());
        DiffResult diffResult = diff.compare();

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(byteStream);
        diffResult.printChangeLog(outputStream, targetDatabaseConnection);

        return byteStream.toString("utf-8");
    }

    /**
     * Generates initial changelog.xml
     * @param author
     * @param context
     * @param dataDir
     * 
     * @throws DatabaseException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public String init(String author)
                     throws DatabaseException, IOException, ParserConfigurationException {

        Diff diff = new Diff(this.databaseConnection, defaultSchemaName);
        diff.setDiffTypes("");

        diff.addStatusListener(new OutDiffStatusListener());
        DiffResult diffResult = diff.compare();
        diffResult.setChangeSetAuthor(author);
        diffResult.setChangeSetContext("");
        diffResult.setDataDir("");

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(byteStream);
        diffResult.printChangeLog(outputStream, this.databaseConnection);

        return byteStream.toString("utf-8");
    }

    public void initAndSave(String author) throws Exception {
        String data = this.init(author);

        BufferedWriter out = new BufferedWriter(new FileWriter(this.changelog));
        out.write(data);
        out.close();
    }


    private static class OutDiffStatusListener implements DiffStatusListener {
        public void statusUpdate(String message) {
            LogFactory.getLogger().info(message);
        }

    }
}

