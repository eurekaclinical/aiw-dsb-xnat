package edu.emory.cci.aiw.dsb.xnat;

/*-
 * #%L
 * XNAT Data Source Backend
 * %%
 * Copyright (C) 2015 - 2016 Emory University
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.protempa.DataSourceReadException;
import org.protempa.DataStreamingEventIterator;
import org.protempa.KnowledgeSource;
import org.protempa.KnowledgeSourceReadException;
import org.protempa.QuerySession;
import org.protempa.backend.AbstractCommonsDataSourceBackend;
import org.protempa.backend.DataSourceBackendFailedConfigurationValidationException;
import org.protempa.backend.DataSourceBackendFailedDataValidationException;
import org.protempa.backend.annotations.BackendInfo;
import org.protempa.backend.annotations.BackendProperty;
import org.protempa.backend.dsb.DataValidationEvent;
import org.protempa.backend.dsb.filter.Filter;
import org.protempa.dest.QueryResultsHandler;
import org.protempa.proposition.Proposition;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Andrew Post
 */
@BackendInfo(displayName = "XNAT")
public class XNATDataSourceBackend extends AbstractCommonsDataSourceBackend {

    private String project;
    private String scheme;
    private String hostname;
    private int port;
    private String path;
    private String username;
    private String password;


    public XNATDataSourceBackend() {
        this.scheme = "http";
    }

    @Override
    public String getKeyType() {
        return "Patient";
    }

    @Override
    public String getKeyTypeDisplayName() {
        return "patient";
    }

    @BackendProperty
    public void setProject(String project) {
        this.project = project;
    }

    public String getProject() {
        return project;
    }

    public String getScheme() {
        return scheme;
    }

    @BackendProperty
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHostname() {
        return hostname;
    }

    @BackendProperty
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    @BackendProperty
    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    @BackendProperty
    public void setPath(String path) {
        this.path = path;
    }

    public String getUsername() {
        return username;
    }

    @BackendProperty
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    @BackendProperty
    public void setPassword(String password) {
        this.password = password;
    }

    @BackendProperty(propertyName = "url")
    public void parseUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        this.scheme = uri.getScheme();
        this.hostname = uri.getHost();
        this.port = uri.getPort();
        this.path = uri.getPath();
    }

    @Override
    public DataStreamingEventIterator<Proposition> readPropositions(Set<String> keyIds, Set<String> propIds, Filter filters, QuerySession qs, QueryResultsHandler queryResultsHandler) throws DataSourceReadException {
        if (this.project == null) {
            throw new DataSourceReadException("A project must be specified");
        }
        Set<String> subjectIds = new HashSet<>();
        if (keyIds != null && !keyIds.isEmpty()) {
            subjectIds.addAll(keyIds);
        } else {
            try {
                DefaultHttpClient http = new DefaultHttpClient();
                String userpass = username + ":" + password;
                String encoded = new String(Base64.getEncoder().encode(userpass.getBytes()));
                URI uri = new URI(this.scheme, null, this.hostname, this.port,
                        this.path + "data/archive/projects/" + this.project + "/subjects/",
                        "format=xml", null);
                System.out.println("uri: " + uri);
                HttpGet get = new HttpGet(uri);
                get.addHeader("Authorization", "Basic " + encoded);
                HttpResponse resp = http.execute(get);
                try (InputStream contentIn = resp.getEntity().getContent()) {
                    Document doc = DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder()
                            .parse(contentIn);
                    NodeList elementsByTagName = doc.getElementsByTagName("row");
                    for (int i = 0, n = elementsByTagName.getLength(); i < n; i++) {
                        Node row = elementsByTagName.item(i);
                        NodeList cells = row.getChildNodes();
                        subjectIds.add(cells.item(0).getTextContent().trim());
                    }
                } catch (SAXException | ParserConfigurationException ex) {
                    throw new DataSourceReadException("Unexpected XML returned", ex);
                }
            } catch (URISyntaxException ex) {
                throw new DataSourceReadException("Invalid URL for retrieving subject list", ex);
            } catch (IOException ex) {
                throw new DataSourceReadException(ex);
            }
        }

        return new XNATExperimentIterator(getId(), this.project, subjectIds, this.scheme, this.hostname, this.port, this.path, this.username, this.password, propIds, filters);
    }

    @Override
    public DataValidationEvent[] validateData(KnowledgeSource knowledgeSource) throws DataSourceBackendFailedDataValidationException, KnowledgeSourceReadException {
        return new DataValidationEvent[0];
    }

    @Override
    public void validateConfiguration(KnowledgeSource knowledgeSource) throws DataSourceBackendFailedConfigurationValidationException, KnowledgeSourceReadException {
    }
}
