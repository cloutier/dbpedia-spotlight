/*
 * Copyright 2011 Pablo Mendes, Max Jakob
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Check our project website for information on how to acknowledge the authors and how to contribute to the project: http://spotlight.dbpedia.org
 */

package org.dbpedia.spotlight.lucene.index

import scalaj.collection.Imports._
import org.dbpedia.spotlight.lucene.LuceneManager
import io.Source
import org.dbpedia.spotlight.model.DBpediaResource
import org.apache.lucene.store.FSDirectory
import org.apache.avalon.framework.configuration.ConfigurationException
import org.apache.commons.logging.{LogFactory, Log}
import java.io.{FileInputStream, File}
import org.dbpedia.spotlight.util.{ExtractCandidateMap, TypesLoader, IndexingConfiguration}

/**
 * This script goes over the index and adds surface forms, counts and DBpedia Types as provided in input files.
 *
 * @author pablomendes
 */

object PatchIndex {

    val LOG: Log = LogFactory.getLog(this.getClass)

    def uri2count(line : String) = {
        if (line.trim != null) {
            val fields = line.trim.split("\\s+");
            (fields(0), fields(1).toInt) // extracts a map from uri2count from the csv line
        } else {
            ("",0) //
        }
    }

    def main(args : Array[String]) {
        val indexingConfigFileName = args(0)
        val countsFileName = args(1)

        val config = new IndexingConfiguration(indexingConfigFileName)
        val indexFileName = config.get("org.dbpedia.spotlight.index.dir")
        val instanceTypesFileName = config.get("org.dbpedia.spotlight.data.instanceTypes")
        val surfaceFormsFileName = config.get("org.dbpedia.spotlight.data.surfaceForms")

        val indexFile = new File(indexFileName)
        if (!indexFile.exists)
            throw new ConfigurationException("index dir "+indexFile+" does not exist")
        if (!new File(countsFileName).exists)
            throw new ConfigurationException("counts file "+countsFileName+" does not exist")
        if (!new File(instanceTypesFileName).exists)
            throw new ConfigurationException("types file "+instanceTypesFileName+" does not exist")
        if (!new File(surfaceFormsFileName).exists)
            throw new ConfigurationException("surface forms file "+surfaceFormsFileName+" does not exist")

        val luceneManager = new LuceneManager.BufferedMerging(FSDirectory.open(indexFile))


        val typesMap = AddTypesToIndex.loadTypes(instanceTypesFileName);
        val countsMap = AddCountsToIndex.loadCounts(countsFileName)
        val sfMap = AddSurfaceFormsToIndex.loadSurfaceForms(surfaceFormsFileName, AddSurfaceFormsToIndex.fromTitlesToAlternatives)

        val sfIndexer = new IndexEnricher(luceneManager)
        LOG.info("Expunge deletes.")
        sfIndexer.expunge();
        LOG.info("Done.")

        LOG.info("Patching up index.")
        sfIndexer.patchAll(typesMap, countsMap, sfMap)
        LOG.info("Done.")
        sfIndexer.close
    }

}