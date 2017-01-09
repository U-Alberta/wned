/*
 * Copyright 2017 Zhaochen Guo
 *
 * This file is part of WNED.
 * WNED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * WNED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with WNED.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.ualberta.entitylinking.common.indexing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.PageCallbackHandler;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiPage;
import ca.ualberta.entitylinking.kb.wikipedia.wikixmlj.WikiXMLSAXParser;

public class WikipediaIndex implements PageCallbackHandler {
	private IndexReader reader = null;
	private IndexWriter writer = null;
	private IndexSearcher searcher = null;

	public WikipediaIndex() {
	}
	
	public void process(WikiPage page) {
		if (page == null) return;
		String title = page.getTitle().trim();
		
		try {
			//ignore special pages, stub pages, redirect pages, and disambiguation pages.
			if (page.isSpecialPage() ||
				page.isStub() || 
				page.isRedirect() ||
				page.isDisambiguationPage())
				
				return;
			
			//entity page.
			String content = page.getText();
			if (title == null || title.isEmpty() || content == null || content.isEmpty())
				return;

			content = DocumentIndexer.getPlainText(content);
			addDocument(title, content);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void indexWikipedia(String indexDir, String wikiFile) {
		initWriter(indexDir, true);

		try {
			WikiXMLSAXParser.parseWikipediaDump(wikiFile, this);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("DONE!!!!!!!1");
	
		//finalize.
		finalize();
	}
	
	public void initWriter(String indexDir, boolean create) {
		try {
			Directory dir = FSDirectory.open(new File(indexDir));
			IndexWriterConfig iwc = new IndexWriterConfig(
					Version.LUCENE_34, 
					new StandardAnalyzer(Version.LUCENE_34));

			// create a new index
			if (create)
				iwc.setOpenMode(OpenMode.CREATE);
			else
				iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);

			writer = new IndexWriter(dir, iwc);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addDocument(String name, String content) {
		if (name == null || name.isEmpty() || content == null || content.isEmpty())
			return;
		
		try {
			// index a document.
			Document doc = new Document();
			doc.add(new Field("docID", name, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
			doc.add(new Field("content", content.toLowerCase(), Field.Store.YES,
						Field.Index.ANALYZED, Field.TermVector.YES));
			
			writer.addDocument(doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void finalize() {
		try {
			writer.optimize();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadIndex(String diskDir) {
		Directory dir = null;
		
		try {
			dir = new RAMDirectory(new MMapDirectory(new File(diskDir)));
			if (!IndexReader.indexExists(dir))
				return;
			
			reader = IndexReader.open(dir);
			searcher = new IndexSearcher(reader);
			System.out.println("Loading WikipediaIndex done!!!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public List<String> query(String queryStr, int n) {
		if (queryStr == null || queryStr.isEmpty())
			return null;
		
		List<String> set = new ArrayList<String>();
		
		try {
			queryStr = queryStr.toLowerCase();

			//Just do a quick search
			QueryParser parser = new QueryParser(Version.LUCENE_34, "content", new StandardAnalyzer(Version.LUCENE_34));
			Query query = parser.parse(queryStr);
			n = n > 5 ? 5: n;
			TopDocs td = searcher.search(query, n);
			
			if (td == null || td.totalHits == 0) {
				System.out.println("No hits");
				return null;
			}
			
			for (int i = 0; i < td.scoreDocs.length; i++) {
				int docId = td.scoreDocs[i].doc;
				String name = reader.document(docId).get("docID");
				set.add(name);

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return set;
	}

	public static void main(String[] argv) {
		WikipediaIndex indexer = new WikipediaIndex();
		indexer.indexWikipedia(argv[0], argv[1]);
	}
}
