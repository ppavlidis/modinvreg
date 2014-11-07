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

package ubc.pavlab.rdp.server.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

import org.json.JSONArray;
import org.springframework.beans.factory.InitializingBean;

import ubc.pavlab.rdp.server.model.Gene;
import ubc.pavlab.rdp.server.model.GeneOntologyTerm;
import ubc.pavlab.rdp.server.model.Taxon;
import ubc.pavlab.rdp.server.service.GeneOntologyServiceImpl.GOAspect;
import ubic.basecode.ontology.model.OntologyTerm;

/**
 * ubic.gemma.ontology.providers.GeneOntologyService
 * 
 * @author mjacobson
 * @version $Id$
 */
public interface GeneOntologyService extends InitializingBean {

    public static final String BASE_GO_URI = "http://purl.obolibrary.org/obo/";

    public abstract Map<OntologyTerm, Long> calculateGoTermFrequency( Collection<Gene> genes, Long taxonId,
            int minimumFrequency, int minimumTermSize, int maximumTermSize );

    /**
     * <p>
     * Given a query Gene, and a collection of gene ids calculates the go term overlap for each pair of queryGene and
     * gene in the given collection. Returns a Map<Gene,Collection<OntologyEntries>>. The key is the gene (from the
     * [queryGene,gene] pair) and the values are a collection of the overlapping ontology entries.
     * </p>
     * 
     * @param queryGene
     * @param geneIds
     * @returns map of gene ids to collections of ontologyTerms. This will always be populated but collection values
     *          will be empty when there is no overlap.
     */
    public abstract Map<Long, Collection<OntologyTerm>> calculateGoTermOverlap( Gene queryGene, Collection<Long> geneIds );

    /**
     * @param queryGene1
     * @param queryGene2
     * @returns Collection<OntologyEntries>
     */
    public abstract Collection<OntologyTerm> calculateGoTermOverlap( Gene queryGene1, Gene queryGene2 );

    public abstract Map<Long, Collection<OntologyTerm>> calculateGoTermOverlap( Long queryGene, Collection<Long> geneIds );

    /**
     * @param masterOntos
     * @param comparisonOntos
     * @return
     */
    public abstract Collection<OntologyTerm> computeOverlap( Collection<OntologyTerm> masterOntos,
            Collection<OntologyTerm> comparisonOntos );

    /**
     * Search by inexact string
     * 
     * @param queryString
     * @return
     */
    public abstract Collection<OntologyTerm> findTerm( String queryString );

    public abstract Collection<GeneOntologyTerm> fetchByQuery( String queryString );

    /**
     * @param entry
     * @return children, NOT including part-of relations.
     */
    public abstract Collection<OntologyTerm> getAllChildren( OntologyTerm entry );

    /**
     * @param entry
     * @param includePartOf
     * @return
     */
    public abstract Collection<OntologyTerm> getAllChildren( OntologyTerm entry, boolean includePartOf );

    /**
     * @return a collection of all existing GO term ids (GO_XXXXXXX) -- including the roots of the ontologies
     *         ('biological process' etc.)
     */
    public abstract Collection<String> getAllGOTermIds();

    /**
     * @param entries NOTE terms that are in this collection are NOT explicitly included; however, some of them may be
     *        included incidentally if they are parents of other terms in the collection.
     * @return
     */
    public abstract Collection<OntologyTerm> getAllParents( Collection<OntologyTerm> entries );

    /**
     * @param entries
     * @param includePartOf
     * @return
     */
    public abstract Collection<OntologyTerm> getAllParents( Collection<OntologyTerm> entries, boolean includePartOf );

    /**
     * Return all the parents of GO OntologyEntry, up to the root, as well as terms that this has a restriction
     * relationship with (part_of). NOTE: the term itself is NOT included; nor is the root.
     * 
     * @param entry
     * @return parents (excluding the root)
     */
    public abstract Collection<OntologyTerm> getAllParents( OntologyTerm entry );

    public abstract Collection<OntologyTerm> getAllParents( OntologyTerm entry, boolean includePartOf );

    /**
     * Returns the immediate children of the given entry
     * 
     * @param entry
     * @return children of entry, or null if there are no children (or if entry is null)
     */

    public abstract Collection<OntologyTerm> getChildren( OntologyTerm entry );

    public abstract Collection<OntologyTerm> getChildren( OntologyTerm entry, boolean includePartOf );

    /**
     * @param goId
     * @param taxon
     * @return Collection of all genes in the given taxon that are annotated with the given id, including its child
     *         terms in the hierarchy.
     */
    public abstract Collection<Gene> getGenes( String goId, Taxon taxon );

    public abstract Collection<Gene> getGenes( String goId, Long taxonId );

    public abstract Collection<Gene> getGenes( OntologyTerm t, Long taxonId );

    public abstract Long getGeneSize( OntologyTerm t );

    public abstract Long getGeneSize( String id );

    public abstract Long getDirectGeneSize( OntologyTerm t );

    /**
     * @param Take a gene and return a set of all GO terms including the parents of each GO term
     * @param geneOntologyTerms
     */
    public abstract Collection<OntologyTerm> getGOTerms( Gene gene );

    /**
     * Get all GO terms for a gene, including parents of terms via is-a relationships; and optionally also parents via
     * part-of relationships.
     * 
     * @param gene
     * @param includePartOf
     * @return
     */
    public abstract Collection<OntologyTerm> getGOTerms( Gene gene, boolean includePartOf );

    /**
     * Get all GO terms for a gene, including parents of terms via is-a relationships; and optionally also parents via
     * part-of relationships.
     * 
     * @param gene
     * @param includePartOf
     * @param goAspect limit only to the given aspect (pass null to use all)
     * @return
     */
    public abstract Collection<OntologyTerm> getGOTerms( Gene gene, boolean includePartOf, GOAspect goAspect );

    public abstract Collection<OntologyTerm> getGOTerms( Gene gene, boolean includePartOf, GOAspect goAspect,
            boolean includeParents );

    public abstract Collection<OntologyTerm> getGOTerms( Long geneId );

    /**
     * Return the immediate parent(s) of the given entry. The root node is never returned.
     * 
     * @param entry
     * @return collection, because entries can have multiple parents. (only allroot is excluded)
     */
    public abstract Collection<OntologyTerm> getParents( OntologyTerm entry );

    /**
     * @param entry
     * @param includePartOf
     * @return the immediate parents of the given ontology term. includePartOf determins if part of relationships are
     *         included in the returned information
     */
    public abstract Collection<OntologyTerm> getParents( OntologyTerm entry, boolean includePartOf );

    /**
     * @param goId
     * @return
     */
    public GOAspect getTermAspect( String goId );

    /**
     * Return a definition for a GO Id.
     * 
     * @param goId e.g. GO:0094491
     * @return Definition or null if there is no definition.
     */
    public abstract String getTermDefinition( String goId );

    public abstract String getTermDefinition( OntologyTerm t );

    /**
     * Return human-readable term ("protein kinase") for a GO Id.
     * 
     * @param goId
     * @return
     */
    public abstract String getTermName( String goId );

    /**
     * 
     */
    public abstract void init( boolean force );

    /**
     * Determines if one ontology entry is a child (direct or otherwise) of a given parent term.
     * 
     * @param parent
     * @param potentialChild
     * @return
     */
    public abstract Boolean isAChildOf( OntologyTerm parent, OntologyTerm potentialChild );

    /**
     * Determines if one ontology entry is a parent (direct or otherwise) of a given child term.
     * 
     * @param child
     * @param potentialParent
     * @return True if potentialParent is in the parent graph of the child; false otherwise.
     */
    public abstract Boolean isAParentOf( OntologyTerm child, OntologyTerm potentialParent );

    /**
     * @param goId e.g. GO:0000244 or as GO_0000244
     * @return
     * @throws an exception of GO isn't ready.
     */
    public abstract Boolean isAValidGOId( String goId );

    /**
     * @param term
     * @return
     */
    public abstract boolean isBiologicalProcess( OntologyTerm term );

    /**
     * Used for determining if the Gene Ontology has finished loading into memory yet Although calls like getParents,
     * getChildren will still work (its much faster once the gene ontologies have been preloaded into memory.
     * 
     * @returns boolean
     */
    public abstract boolean isGeneOntologyLoaded();

    public abstract boolean isReady();

    public abstract boolean isRunning();

    public abstract Collection<OntologyTerm> listTerms();

    /**
     * Primarily here for testing.
     * 
     * @param is
     * @throws IOException
     */
    public abstract void loadTermsInNameSpace( InputStream is );

    /**
     * Primarily here for testing, to recover memory.
     */
    public abstract void shutDown();

    public abstract Collection<GeneOntologyTerm> deserializeGOTerms( String[] GOJSON );

    public abstract Long computeOverlapFrequency( String id, Collection<Gene> genes );

    public abstract Collection<Gene> getRelatedGenes( Collection<GeneOntologyTerm> goTerms, Long taxonId );

    public abstract Long getGeneSizeInTaxon( OntologyTerm t, Long taxonId );

    public abstract Long getGeneSizeInTaxon( String id, Long taxonId );

    public abstract Long getDirectGeneSizeInTaxon( OntologyTerm t, Long taxonId );

    public abstract JSONArray toJSON( Collection<GeneOntologyTerm> goTerms );

}