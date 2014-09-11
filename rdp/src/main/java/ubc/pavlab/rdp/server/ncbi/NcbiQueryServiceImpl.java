/*
 * The rdp project
 * 
 * Copyright (c) 2014 University of British Columbia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ubc.pavlab.rdp.server.ncbi;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ubc.pavlab.rdp.server.exception.NcbiServiceException;
import ubc.pavlab.rdp.server.model.Gene;
import ubc.pavlab.rdp.server.model.GeneAlias;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * Calls NCBI E-Utils SOAP query service.
 * 
 * @author mjacobson
 * @version $Id$
 */
@Service
public class NcbiQueryServiceImpl implements NcbiQueryService {
    private static final String NCBI_URL = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/";

    private static final Map<String, String> TAXON_COMMON_TO_ID = new HashMap<String, String>();

    static {
        // TAXON_COMMON_TO_ID.put( "Human", "9606" );
        // TAXON_COMMON_TO_ID.put( "Mouse", "10090" );
        // TAXON_COMMON_TO_ID.put( "Rat", "10116" );
        TAXON_COMMON_TO_ID.put( "Yeast", "559292" );
        // TAXON_COMMON_TO_DATASET.put("Zebrafish","7955");
        // TAXON_COMMON_TO_DATASET.put("Fruitfly","7227");
        // TAXON_COMMON_TO_DATASET.put("Worm","6239");
        // TAXON_COMMON_TO_DATASET.put("E-coli","562");
    }

    private static Log log = LogFactory.getLog( NcbiQueryServiceImpl.class.getName() );

    @Autowired
    private NcbiCache ncbiCache;

    /*
     * (non-Javadoc)
     * 
     * @see ubc.pavlab.rdp.server.ncbi.NcbiQueryService#fetchGenesByGeneSymbols(java.util.Collection, java.lang.String)
     */
    @Override
    public Collection<Gene> fetchGenesByGeneSymbolsAndTaxon( Collection<String> geneSymbols, String taxon )
            throws NcbiServiceException {
        updateCacheIfExpired();

        return ncbiCache.fetchGenesByGeneSymbolsAndTaxon( geneSymbols, taxon );
    }

    /*
     * (non-Javadoc)
     * 
     * @see ubc.pavlab.rdp.server.ncbi.NcbiQueryService#fetchGenesByGeneSymbols(java.util.Collection)
     */
    @Override
    public Collection<Gene> fetchGenesByGeneSymbols( Collection<String> geneSymbols ) throws NcbiServiceException {
        updateCacheIfExpired();

        return ncbiCache.fetchGenesByGeneSymbols( geneSymbols );
    }

    /*
     * (non-Javadoc)
     * 
     * @see ubc.pavlab.rdp.server.ncbi.NcbiQueryService#findGenes(java.lang.String, java.lang.String)
     */
    @Override
    public Collection<Gene> findGenes( String queryString, String taxon ) throws NcbiServiceException {
        updateCacheIfExpired();

        return ncbiCache.findGenes( queryString, taxon );
    }

    /*
     * (non-Javadoc)
     * 
     * @see ubc.pavlab.rdp.server.ncbi.NcbiQueryService#getGenes(java.util.List)
     */
    @Override
    public List<Gene> getGenes( List<String> geneStrings ) throws NcbiServiceException {
        updateCacheIfExpired();

        return ncbiCache.getGenes( geneStrings );
    }

    @SuppressWarnings("unused")
    @PostConstruct
    private void initialize() throws NcbiServiceException {
        updateCacheIfExpired();
    }

    private static Document loadXMLFromString( String xml ) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource( new StringReader( xml ) );
        return builder.parse( is );
    }

    private static String getCharacterDataFromElement( Element e ) {
        Node child = e.getFirstChild();
        if ( child instanceof CharacterData ) {
            CharacterData cd = ( CharacterData ) child;
            return cd.getData();
        }
        return "?";
    }

    private void updateCacheIfExpired() throws NcbiServiceException {
        if ( !this.ncbiCache.hasExpired() ) return;

        Collection<Gene> genes = new HashSet<>();

        for ( Map.Entry<String, String> taxon : TAXON_COMMON_TO_ID.entrySet() ) {
            // updateCacheIfExpired( taxon.getKey(), true );
            genes.addAll( queryNCBI( taxon.getKey() ) );
        }
        log.info( "Caching a total of " + genes.size() + " genes" );
        this.ncbiCache.putAll( genes );
    }

    private Collection<Gene> queryNCBI( final String taxon ) throws NcbiServiceException {

        String taxonID = TAXON_COMMON_TO_ID.get( taxon );

        final StopWatch timer = new StopWatch();
        timer.start();

        Timer uploadCheckerTimer = new Timer( true );
        uploadCheckerTimer.scheduleAtFixedRate( new TimerTask() {
            @Override
            public void run() {
                log.info( "Waiting for NCBI response for (" + taxon + ")... " + timer.getTime() + " ms" );
            }
        }, 0, 10 * 1000 );

        MultivaluedMap<String, String> queryData = new MultivaluedMapImpl();
        queryData.add( "db", "gene" );
        queryData.add( "term", taxonID + "[Taxonomy ID] AND alive[property]" );
        queryData.add( "retmode", "json" );
        queryData.add( "usehistory", "y" );

        String response = sendRequest( queryData, "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi" );

        JSONObject json = new JSONObject( response );

        String webenv = json.getJSONObject( "esearchresult" ).getString( "webenv" );
        String querykey = json.getJSONObject( "esearchresult" ).getString( "querykey" );
        int count = json.getJSONObject( "esearchresult" ).getInt( "count" );

        // log.info( response );
        log.info( "Total genes to parse: " + count );

        Collection<Gene> genes = new HashSet<>();

        for ( int retstart = 0; retstart < count; retstart += 10000 ) {

            queryData = new MultivaluedMapImpl();
            queryData.add( "db", "gene" );
            queryData.add( "query_key", querykey );
            queryData.add( "WebEnv", webenv );
            // queryData.add( "retmode", "json" );
            queryData.add( "retstart", Integer.toString( retstart ) );
            queryData.add( "retmax", "10000" );

            response = sendRequest( queryData, "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi" );

            try {
                Document xmldoc = loadXMLFromString( response );
                response = ""; // Big string, clear some memory
                NodeList nodes = xmldoc.getElementsByTagName( "DocSum" );

                for ( int i = 0; i < nodes.getLength(); i++ ) {
                    Element element = ( Element ) nodes.item( i );

                    Gene gene = new Gene();
                    gene.setTaxon( taxon );

                    Element ID = ( Element ) element.getElementsByTagName( "Id" ).item( 0 );
                    gene.setNcbiGeneId( getCharacterDataFromElement( ID ) );
                    gene.setEnsemblId( getCharacterDataFromElement( ID ) );

                    NodeList items = element.getElementsByTagName( "Item" );
                    int conditionBits = 0;
                    for ( int j = 0; j < items.getLength(); j++ ) {
                        Element item = ( Element ) items.item( j );

                        String name = item.getAttribute( "Name" );

                        switch ( name ) {
                            case "Name":
                                gene.setOfficialSymbol( item.getTextContent() );
                                conditionBits = conditionBits | ( 1 << 0 ); // Set first bit to 1
                                break;
                            case "Description":
                                gene.setOfficialName( item.getTextContent() );
                                conditionBits = conditionBits | ( 1 << 1 ); // Set second bit to 1
                                break;
                            case "OtherAliases":
                                conditionBits = conditionBits | ( 1 << 2 );
                                for ( String alias : item.getTextContent().split( "," ) ) {
                                    gene.getAliases().add( new GeneAlias( alias.trim() ) );
                                }
                                break;
                        }

                        // Short circuit for-loop if all relevant info has been collected,
                        // integer to check against is ( 2^N - 1 ) where N is the # of cases
                        if ( conditionBits == 7 ) {
                            break;
                        }

                    }

                    genes.add( gene );

                }
            } catch ( Exception e ) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            // log.info( response );

            // FIXME replace with something that will check to see when ratelimiting is necessary
            try {
                Thread.sleep( 1000 );
            } catch ( InterruptedException ex ) {
                Thread.currentThread().interrupt();
            }

            log.info( "Genes loaded so far: " + genes.size() );
        }

        log.info( "NCBI request to for (" + taxon + ") took " + timer.getTime() + " ms" );

        uploadCheckerTimer.cancel();

        return genes;
    }

    private static String sendRequest( MultivaluedMap<String, String> queryData, String url )
            throws NcbiServiceException {
        Client client = Client.create();

        WebResource resource = client.resource( url ).queryParams( queryData );

        ClientResponse response = resource.type( MediaType.APPLICATION_FORM_URLENCODED_TYPE )
                .get( ClientResponse.class );

        // Check return code
        if ( Response.Status.fromStatusCode( response.getStatus() ).getFamily() != Response.Status.Family.SUCCESSFUL ) {
            String errorMessage = "Error occurred when accessing BioMart web service: "
                    + response.getEntity( String.class );
            log.error( errorMessage );

            throw new NcbiServiceException( errorMessage );
        }

        return response.getEntity( String.class );
    }

}