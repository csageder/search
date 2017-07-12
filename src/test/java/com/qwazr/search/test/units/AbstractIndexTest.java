/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.test.units;

import com.qwazr.search.annotations.AnnotatedIndexService;
import com.qwazr.search.index.ExplainDefinition;
import com.qwazr.search.index.IndexManager;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.index.ResultDefinition;
import com.qwazr.utils.FileUtils;
import com.qwazr.utils.LoggerUtils;
import org.junit.AfterClass;
import org.junit.Assert;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public abstract class AbstractIndexTest<T> {

	private static Path rootDirectory;
	static IndexManager indexManager;

	static final Logger LOGGER = LoggerUtils.getLogger(AbstractIndexTest.class);

	private final Class<T> recordClass;
	protected final AnnotatedIndexService<T> indexService;

	protected AbstractIndexTest(Class<T> recordClass) {
		this.recordClass = recordClass;
		try {
			indexService = indexManager == null ? null : indexManager.getService(recordClass);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	protected static void initIndexManager() throws IOException, URISyntaxException {
		rootDirectory = Files.createTempDirectory("qwazr_index_test");
		indexManager = new IndexManager(rootDirectory, null);
	}

	protected static <T> AnnotatedIndexService<T> initIndexService(Class<T> recordClass)
			throws URISyntaxException, IOException {
		if (indexManager == null)
			initIndexManager();
		final AnnotatedIndexService<T> indexService = indexManager.getService(recordClass);
		indexService.createUpdateSchema();
		indexService.createUpdateIndex();
		indexService.createUpdateFields();
		return indexService;
	}

	ResultDefinition.WithObject<T> checkQuery(QueryDefinition queryDef, Long hitsExpected, String queryDebug) {
		final ResultDefinition.WithObject<T> result = indexService.searchQuery(queryDef);
		Assert.assertNotNull(result);
		if (result.query != null)
			LOGGER.info(result.query);
		if (hitsExpected != null) {
			Assert.assertEquals(hitsExpected, result.total_hits);
			if (hitsExpected > 0) {
				ExplainDefinition explain = indexService.explainQuery(queryDef, result.documents.get(0).getDoc());
				Assert.assertNotNull(explain);
			}
		}
		if (queryDebug != null)
			Assert.assertEquals(queryDebug, result.getQuery());
		return result;
	}

	ResultDefinition.WithObject<T> checkQuery(QueryDefinition queryDef) {
		return checkQuery(queryDef, 1L, null);
	}

	@AfterClass
	public static void afterClass() throws IOException {
		if (indexManager != null) {
			indexManager.close();
			indexManager = null;
		}
		if (rootDirectory != null)
			FileUtils.deleteDirectoryQuietly(rootDirectory);
	}

	public static abstract class WithIndexRecord extends AbstractIndexTest<IndexRecord> {

		protected static AnnotatedIndexService<IndexRecord> indexService;

		public WithIndexRecord() {
			super(IndexRecord.class);
		}

		static void initIndexService() throws URISyntaxException, IOException {
			indexService = AbstractIndexTest.initIndexService(IndexRecord.class);
		}
	}
}
