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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.lang.Runnable;

import ca.ualberta.entitylinking.config.WNEDConfig;
import org.wikipedia.miner.annotation.Disambiguator;
import org.wikipedia.miner.model.Wikipedia;
import org.wikipedia.miner.model.Page;
import org.wikipedia.miner.util.WikipediaConfiguration;
import org.wikipedia.miner.util.PageIterator;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.FieldCache;

public class DocumentIndexer {
	public static String wikiConfigFile = WNEDConfig.wikiConfigFile;

	private Wikipedia wikipedia = null;
	private Disambiguator disambiguator = null;

	public static class Input {
		public Input(String id, String content) {
			this.id = id;
			this.content = content;
		}

		public String id;
		public String content;
	}

	public static class Output {
		public Output(String id, List<Tokenizer.Token> tokens) {
			this.id = id;
			this.tokens = tokens;
		}

		public String id;
		public List<Tokenizer.Token> tokens;
	}

	public static class IndexThread implements Runnable {
		BlockingQueue<Output> outputQueue;
		DocumentIndexer indexer;

		public IndexThread(DocumentIndexer indexer, BlockingQueue<Output> outputQueue) {
			this.outputQueue = outputQueue;
			this.indexer = indexer;
		}

		public void run() {
			int count = 0;
			while (true) {
				if (count++ % 1000 == 0)
					System.out.println("Indexing pages: " + count);

				try {
					Output item = outputQueue.take();
					if (item.id == null && item.tokens == null)
						break;

					indexer.indexing(item.id, item.tokens);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			System.out.println("Done with indexing!!!");
		}
	}

	public DocumentIndexer() {
		this(wikiConfigFile);
	}

	public DocumentIndexer(String wikiConfigFile) {
		//Configure the Wikipedia Miner.
		try {
			File confFile = new File(wikiConfigFile);
			WikipediaConfiguration conf = new WikipediaConfiguration(confFile);
			wikipedia = new Wikipedia(conf, false);
			disambiguator = new Disambiguator(wikipedia);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** the following functions are mainly for testing.
	 */
	public static String getPlainText(String content) {
		String text = content.replaceAll("&gt;", ">");
		text = text.replaceAll("&lt;", "<");
		text = text.replaceAll("(?s)<ref.*?</ref>", " ");
		text = text.replaceAll("</?.*?>", " ");
		text = text.replaceAll("(?s)\\{\\{.*?\\}\\}", " ");
		text = text.replaceAll("\\[\\[.*?:.*?\\]\\]", " ");
//		text = text.replaceAll("\\[\\[(\\w+)\\|(\\w+)\\]\\]", "$2");
		text = text.replaceAll("\\[\\[(.*?)\\]\\]", "$1");
		text = text.replaceAll("(?s)\\<\\!\\-\\-(.*?)\\-\\-\\>", " ");
		text = text.replaceAll("\\[.*?\\]", " ");
		text = text.replaceAll("\\'+", "");
		text = text.replaceAll("\\|", " ");
		return text;
	}

	public void testPageIter() {
		int count = 0;
		PageIterator pageIter = wikipedia.getPageIterator(Page.PageType.article);
		while (pageIter.hasNext()) {
			count++;
			if (count % 1000 == 0)
				System.out.println("Reading pages: " + count);
			Page page = pageIter.next();
			String title = page.getTitle();
			String content = page.getMarkup();
			System.out.println(title);
		}
	}

	public void indexWikipedia(String indexDir) {
		initWriter(indexDir, true);

		int count = 0;

		//index.
		BlockingQueue<Input> inputQueue = new ArrayBlockingQueue<Input>(10);
		BlockingQueue<Output> outputQueue = new ArrayBlockingQueue<Output>(10);

		int THREAD_NUM = 16;
		Thread[] threads = new Thread[THREAD_NUM];
		for (int i = 0; i < threads.length; i++) {
			Tokenizer toker = new Tokenizer(wikipedia, disambiguator, inputQueue, outputQueue);
			threads[i] = new Thread(toker);
			threads[i].start();
		}

		IndexThread indexThread = new IndexThread(this, outputQueue);
		Thread index = new Thread(indexThread);
		index.start();

		PageIterator pageIter = wikipedia.getPageIterator(Page.PageType.article);
		while (pageIter.hasNext()) {
			count++;
			if (count % 1000 == 0)
				System.out.println("Reading pages: " + count);
			Page page = pageIter.next();
			String title = page.getTitle();
			String content = page.getMarkup();
			if (title == null || title.isEmpty() || content == null || content.isEmpty())
				continue;

			content = getPlainText(content);

			try {
				System.out.println(count + "\t" + title);
				inputQueue.put(new Input(title, content));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}


		//at the end of indexing, we put an exit message in the queue.
		try {
			for (int i = 0; i < THREAD_NUM; i++)
				inputQueue.put(new Input(null, null));

			for (int i = 0; i < THREAD_NUM; i++)
				threads[i].join();
		} catch (Exception e) {
			e.printStackTrace();
		}

		pageIter.close();

		try {
			outputQueue.put(new Output(null, null));
			index.join();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("DONE!!!!!!!1");

		//finalize.
		finalize();
	}

	IndexWriter writer = null;
	public void initWriter(String indexDir, boolean create) {
		try {
			Directory dir = FSDirectory.open(new File(indexDir));
			IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_34, null);

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

	public void indexing(String name, List<Tokenizer.Token> tokens) {
		if (name == null || name.isEmpty() || tokens == null || tokens.isEmpty())
			return;

		try {
			// index a document.
			Document doc = new Document();

			doc.add(new Field("name", name, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));

			for (Tokenizer.Token token : tokens) {
				doc.add(new Field("contents", token.text.toLowerCase(), Field.Store.YES,
						Field.Index.NOT_ANALYZED_NO_NORMS, Field.TermVector.YES));
			}

			if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
				writer.addDocument(doc);
			} else {
				writer.updateDocument(new Term("name", name), doc);
			}
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

	public void readLuceneIndex(String indexDir, String docName) {
		IndexReader reader = null;
		Map<String, Integer> name2id = null;

		//load index
		try {
			reader = IndexReader.open(FSDirectory.open(new File(indexDir)));

			String[] stringArray = FieldCache.DEFAULT.getStrings(reader, "name");

			// build a map from string to its document id.
			name2id = new HashMap<String, Integer>();
			for (int i = 0; i < stringArray.length; i++)
				name2id.put(stringArray[i], i);
		} catch (IOException e) {
			e.printStackTrace();
		}

		//get tf-idf vector of a document.
		DefaultSimilarity simObj = new DefaultSimilarity();

		try {
			if (!name2id.containsKey(docName))
				return;

			int docId = name2id.get(docName);
			Document doc = reader.document(docId);

			TermFreqVector termVector = reader.getTermFreqVector(docId, "contents");
			int numDocs = reader.numDocs();

			int[] termFreq = termVector.getTermFrequencies();
			String[] terms = termVector.getTerms();
			for (int i = 0; i < terms.length; i++) {
				//avoid stop words
//				if (isStopWord(terms[i]))
//					continue;

				int tf = termFreq[i];
				int df = reader.docFreq(new Term("contents", terms[i]));
				float tfidf = simObj.tf(tf) * simObj.idf(df, numDocs);
				System.out.println(terms[i] + ": " + tfidf);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		DocumentIndexer indexer = new DocumentIndexer();
		indexer.indexWikipedia(args[0]);
	}
}

