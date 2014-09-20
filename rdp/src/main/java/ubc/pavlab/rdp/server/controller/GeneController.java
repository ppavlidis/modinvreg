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

package ubc.pavlab.rdp.server.controller;

import gemma.gsec.authentication.UserManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.directwebremoting.annotations.RemoteProxy;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import ubc.pavlab.rdp.server.exception.NcbiServiceException;
import ubc.pavlab.rdp.server.model.Gene;
import ubc.pavlab.rdp.server.model.Researcher;
import ubc.pavlab.rdp.server.model.common.auditAndSecurity.User;
import ubc.pavlab.rdp.server.ncbi.NcbiQueryService;
import ubc.pavlab.rdp.server.service.GeneService;
import ubc.pavlab.rdp.server.service.ResearcherService;
import ubc.pavlab.rdp.server.util.JSONUtil;

/**
 * Handles gene-related requests
 * 
 * @author ptan
 * @version $Id$
 */

@Controller
@RemoteProxy
public class GeneController {

    private static Log log = LogFactory.getLog( GeneController.class );

    @Autowired
    protected UserManager userManager;

    @Autowired
    protected ResearcherService researcherService;

    @Autowired
    protected GeneService geneService;

    /*
     * @Autowired protected BioMartQueryService biomartService;
     */

    @Autowired
    protected NcbiQueryService ncbiQueryService;

    /**
     * Returns the list of genes for the given Researcher and Model Organism.
     * 
     * @param request
     * @param response
     */
    @RequestMapping("/loadResearcherGenes.html")
    public void loadResearcherGenes( HttpServletRequest request, HttpServletResponse response ) {
        JSONUtil jsonUtil = new JSONUtil( request, response );
        String jsonText = "";

        String username = userManager.getCurrentUsername();
        final String taxonCommonName = request.getParameter( "taxonCommonName" );

        Researcher researcher = researcherService.findByUserName( username );

        try {

            if ( researcher == null ) {
                // This shouldn't happen.
                log.info( "Could not find researcher associated with account: " + username + ", creating one" );
                researcher = researcherService.create( new Researcher() );
                User contact = ( User ) userManager.getCurrentUser();
                researcher.setContact( contact );
                researcherService.update( researcher );

            }

            Collection<Gene> genes = researcher.getGenes();
            if ( !taxonCommonName.equals( "All" ) ) {
                CollectionUtils.filter( genes, new Predicate() {
                    @Override
                    public boolean evaluate( Object input ) {
                        return ( ( Gene ) input ).getTaxon().equals( taxonCommonName );
                    }
                } );
            }

            jsonText = "{\"success\":true,\"data\":" + ( new JSONArray( genes ) ).toString() + "}";
            log.info( "Loaded " + genes.size() + " genes" );

        } catch ( Exception e ) {
            jsonText = "{\"success\":false,\"message\":\"" + e.getLocalizedMessage() + "\"}";
        } finally {

            try {
                jsonUtil.writeToResponse( jsonText );
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }
    }

    /**
     * AJAX entry point. Loads the Researcher who's currently logged in.
     * 
     * @param request
     * @param response
     */
    @RequestMapping("/findResearchersByGene.html")
    public void findResearchersByGene( HttpServletRequest request, HttpServletResponse response ) throws IOException {

        String jsonText = null;
        JSONUtil jsonUtil = new JSONUtil( request, response );
        String[] genesJSON = request.getParameterValues( "gene[]" );
        String taxonCommonName = request.getParameter( "taxonCommonName" );

        Collection<Researcher> researchers = new HashSet<>();

        try {

            JSONObject json = new JSONObject();

            for ( Gene gene : deserealizeGenes( genesJSON ) ) {
                researchers.addAll( researcherService.findByGene( gene ) );
            }

            Set<String> researchersJson = new HashSet<String>();
            for ( Researcher r : researchers ) {
                researchersJson.add( r.toJSON().toString() );
            }

            JSONArray array = new JSONArray( researchersJson );

            json.put( "data", array.toString() );
            json.put( "success", true );
            json.put( "message", "Found " + researchers.size() + " researchers" );
            jsonText = json.toString();

        } catch ( Exception e ) {
            log.error( e.getLocalizedMessage(), e );
            JSONObject json = new JSONObject();
            json.put( "success", false );
            json.put( "message", e.getLocalizedMessage() );
            jsonText = json.toString();
            log.info( jsonText );
        } finally {
            jsonUtil.writeToResponse( jsonText );
        }

    }

    /**
     * Returns a collection of Gene objects from the json.
     * 
     * @param genesJSON - an array of JSON representations of Genes
     * @return
     */
    private Collection<Gene> deserealizeGenes( String[] genesJSON ) {
        Collection<Gene> results = new HashSet<>();
        for ( int i = 0; i < genesJSON.length; i++ ) {
            JSONObject json = new JSONObject( genesJSON[i] );
            String symbol = json.getString( "officialSymbol" );
            String taxon = json.getString( "taxon" );
            if ( symbol.equals( "" ) || taxon.equals( "" ) ) {
                throw new IllegalArgumentException( "Every gene must have an assigned symbol and organism." );
            }
            Gene geneFound = geneService.findByOfficialSymbol( symbol, taxon );
            if ( !( geneFound == null ) ) {
                results.add( geneFound );
            } else {
                // it doesn't exist yet
                Gene gene = new Gene();
                gene.parseJSON( genesJSON[i] );
                log.info( "Creating new gene: " + gene.toString() );
                results.add( geneService.create( gene ) );
            }
        }

        return results;
    }

    @RequestMapping("/saveResearcherGenes.html")
    public void saveResearcherGenes( HttpServletRequest request, HttpServletResponse response ) throws IOException {

        String jsonText = null;
        JSONUtil jsonUtil = new JSONUtil( request, response );

        String username = userManager.getCurrentUsername();
        // String genesJSON = request.getParameter( "genes" ); //
        // {"ensemblId":"ENSG00000105393","officialSymbol":"BABAM1","officialName":"BRISC and BRCA1 A complex member 1","label":"BABAM1","geneBioType":"protein_coding","key":"BABAM1:human","taxon":"human","genomicRange":{"baseStart":17378159,"baseEnd":17392058,"label":"19:17378159-17392058","htmlLabel":"19:17378159-17392058","bin":65910,"chromosome":"19","tooltip":"19:17378159-17392058"},"text":"<b>BABAM1</b> BRISC and BRCA1 A complex member 1"}
        String taxonCommonName = request.getParameter( "taxonCommonName" );
        String[] genesJSON = request.getParameterValues( "genes[]" );
        String taxonDescriptions = request.getParameter( "taxonDescriptions" );

        if ( genesJSON == null ) {
            log.info( username + ": No genes to save" );
            genesJSON = new String[] {};
        }

        try {
            Researcher researcher = researcherService.findByUserName( username );

            // Update Organism Descriptions
            JSONObject jsonDescriptionSet = new JSONObject( taxonDescriptions );

            for ( Object key : jsonDescriptionSet.keySet() ) {
                String taxon = ( String ) key; //
                String td = jsonDescriptionSet.get( taxon ).toString();
                researcher.updateTaxonDescription( taxon, td );
            }

            // Update Genes
            Collection<Gene> genes = new HashSet<>();
            genes.addAll( deserealizeGenes( genesJSON ) );
            researcherService.updateGenes( researcher, genes ); // This updates the researcher persistence for both

            JSONObject json = new JSONObject();
            json.put( "success", true );
            json.put( "message", "Changes saved" );
            jsonText = json.toString();

        } catch ( Exception e ) {
            log.error( e.getLocalizedMessage(), e );
            JSONObject json = new JSONObject();
            json.put( "success", false );
            json.put( "message", e.getLocalizedMessage() );
            jsonText = json.toString();
            log.info( jsonText );
        } finally {
            jsonUtil.writeToResponse( jsonText );
        }

    }

    /**
     * Force Update NCBI ehcache.
     * 
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestMapping("/updateCache.html")
    public void updateCache( HttpServletRequest request, HttpServletResponse response ) throws IOException {
        JSONUtil jsonUtil = new JSONUtil( request, response );
        String jsonText = null;
        try {
            ncbiQueryService.clearCache();
            int cacheSize = ncbiQueryService.updateCache();

            JSONObject json = new JSONObject();
            json.append( "success", true );
            json.append( "message", "Cache size: " + cacheSize );
            jsonText = json.toString();
        } catch ( Exception e ) {
            log.error( e.getMessage(), e );
            JSONObject json = new JSONObject();
            json.append( "success", false );
            json.append( "message", e.getMessage() );
            jsonText = json.toString();
        } finally {
            jsonUtil.writeToResponse( jsonText );
        }
    }

    /**
     * Finds exact matching gene symbols (case-insensitive).
     * 
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestMapping("/findGenesByGeneSymbols.html")
    public void findGenesByGeneSymbols( HttpServletRequest request, HttpServletResponse response ) throws IOException {
        JSONUtil jsonUtil = new JSONUtil( request, response );
        String jsonText = null;

        final String delimiter = "\n";

        String symbols = request.getParameter( "symbols" );
        Collection<String> querySymbols = new ArrayList<String>(
                Arrays.asList( symbols.toUpperCase().split( delimiter ) ) );
        String taxon = request.getParameter( "taxon" );

        Collection<String> resultSymbols = new ArrayList<>();

        if ( symbols.length() == 0 || querySymbols.size() == 0 ) {
            JSONObject json = new JSONObject();
            json.append( "success", true );
            json.append( "data", resultSymbols );
            json.append( "message", "Please enter a gene symbol" );
            jsonUtil.writeToResponse( json.toString() );
            return;
        }

        try {
            // Collection<Gene> results = biomartService.fetchGenesByGeneSymbols( querySymbols, taxon );
            Collection<Gene> results = ncbiQueryService.fetchGenesByGeneSymbolsAndTaxon( querySymbols, taxon );
            for ( Gene gene : results ) {
                resultSymbols.add( gene.getOfficialSymbol().toUpperCase() );
            }

            // symbols not found
            querySymbols.removeAll( resultSymbols );

            JSONObject json = new JSONObject();
            json.append( "success", true );
            json.append( "data", results );
            if ( querySymbols.size() > 0 ) {
                json.append( "message",
                        querySymbols.size() + " symbols not found: " + StringUtils.join( querySymbols, delimiter ) );
            } else {
                json.append( "message", "All " + results.size() + " symbols were found." );
            }
            jsonText = json.toString();
        } catch ( NcbiServiceException e ) {
            e.printStackTrace();
            log.error( e.getMessage(), e );
            jsonText = "{\"success\":false,\"message\":" + e.getMessage() + "\"}";
        }

        jsonUtil.writeToResponse( jsonText );
        return;
    }

    @RequestMapping("/searchGenes.html")
    public void searchGenes( HttpServletRequest request, HttpServletResponse response ) throws IOException {

        JSONUtil jsonUtil = new JSONUtil( request, response );
        String jsonText = null;

        String query = request.getParameter( "query" );

        // FIXME Handle other taxons
        String taxon = request.getParameter( "taxon" );

        try {
            // Collection<Gene> results = biomartService.findGenes( query, taxon );
            Collection<Gene> results = ncbiQueryService.findGenes( query, taxon );
            jsonText = "{\"success\":true,\"data\":" + ( new JSONArray( results ) ).toString() + "}";
        } catch ( NcbiServiceException e ) {
            e.printStackTrace();
            log.error( e.getMessage(), e );
            jsonText = "{\"success\":false,\"message\":" + e.getMessage() + "\"}";
        }

        jsonUtil.writeToResponse( jsonText );
        return;

    }
}
