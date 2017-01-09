package ca.ualberta.entitylinking.kb.wikipedia.wikixmlj;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.StringReader;


/**
 * For internal use only -- Used by the {@link WikiPage} class.
 * Can also be used as a stand alone class to parse wiki formatted text.
 * @author Delip Rao
 *
 */
public class WikiTextParser {
	
	private String wikiText = null;
	private Vector<String> pageCats = null;
	private Vector<String> pageLinks = null;
	private Vector<Pair<String, String>> linkPairs = null;
	private Vector<Pair<String, Integer>> linkPos = null;
	private boolean stub = false;
	private boolean disambiguation = false;
	private static Pattern stubPattern = Pattern.compile("\\-stub\\}\\}");
	private InfoBox infoBox = null;
	
	public WikiTextParser(String wtext) {
		wikiText = wtext;		

		Matcher matcher = stubPattern.matcher(wikiText);
		stub = matcher.find();
		if (stub)
			return;

		//check if the page is a disambiguation page.
		try {
			String line = null;
			BufferedReader r = new BufferedReader(new StringReader(wtext));
			while ((line = r.readLine()) != null) {
				line = line.toLowerCase().trim();
				if (!line.startsWith("{{"))
					continue;
				
				if (line.startsWith("{{disambig") ||
						line.startsWith("{{airport disambiguation") ||
						line.startsWith("{{call sign disambiguation") ||
						line.startsWith("{{chinese title disambiguation") ||
						line.startsWith("{{geodis") ||
						line.startsWith("{{hdis") ||
						line.startsWith("{{hospital disambiguation") ||
						line.startsWith("{{letter disambiguation") ||
						line.startsWith("{{letter-numbercombdisambig") ||
						line.startsWith("{{mathematical disambiguation") ||
						line.startsWith("{{molecular formula disambiguation") ||
						line.startsWith("{{numberdis") ||
						line.startsWith("{{school disambiguation") ||
						line.startsWith("{{species latin name disambiguation") ||
						line.startsWith("{{wikipedia disambiguation")) {
					disambiguation = true;
					break;
				}
			}
			
			r.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean isStub() {
	  return stub;
	}
	
	public boolean isDisambiguationPage() {
		return disambiguation;
	}

	public String getText() {
		return wikiText;
	}

	public Vector<String> getCategories() {
		if(pageCats == null) parseCategories();
		return pageCats;
	}

	public Vector<String> getLinks() {
		if(pageLinks == null) parseLinks();
		return pageLinks;
	}
	
	public Vector<Pair<String, String>> getLinkPairs() {
		if(linkPairs == null) parseLinks();
		return linkPairs;
	}

	public Vector<Pair<String, Integer>> getLinkPos() {
		if(linkPos == null) parseLinks();
		return linkPos;
	}

	private void parseCategories() {
		pageCats = new Vector<String>();
		Pattern catPattern = Pattern.compile("\\[\\[Category:(.*?)\\]\\]", Pattern.MULTILINE);
		Matcher matcher = catPattern.matcher(wikiText);
		while(matcher.find()) {
			String [] temp = matcher.group(1).split("\\|");
			pageCats.add(temp[0]);
		}
	}
	
	/**
	 * We need to format the entity title to reduce duplicates by removing the 
	 * extra space (e.g. use " " instead of "   "), and capitalizing the first letter of the name.
	 * @param name
	 * @return
	 */
	public static String formatName(String name) {
		name = name.replaceAll("\\s+", " ");
		name = name.replace('_', ' ');
		
		name = name.trim();
		if (name == null || name.isEmpty())
			return null;
		
		if (Character.isUpperCase(name.charAt(0)))
			return name;
		else {
			StringBuilder t = new StringBuilder(name);
			t.setCharAt(0, Character.toUpperCase(name.charAt(0)));
			return t.toString();
		}
	}
	
	private void parseLinks() {
		pageLinks = new Vector<String>();
		linkPairs = new Vector<Pair<String, String>>();
		linkPos = new Vector<Pair<String, Integer>>();
		
		Pattern catPattern = Pattern.compile("\\[\\[(.*?)\\]\\]", Pattern.MULTILINE);
		Matcher matcher = catPattern.matcher(wikiText);
		while(matcher.find()) {
			String [] temp = matcher.group(1).split("\\|");
			if(temp == null || temp.length == 0) continue;
			String entity = temp[0];
			if(WikiPage.isSpecialTitle(entity) == false) {
				entity = formatName(entity);
				if (entity == null || entity.isEmpty())
					continue;
				
				pageLinks.add(entity);
				linkPos.add(new Pair<String, Integer>(entity, matcher.start()));
				
				if (temp.length == 2) {
					String name = temp[1];
					if (name != null) 
						name = formatName(name);

					if (name == null || name.isEmpty())
						continue;
					
					linkPairs.add(new Pair<String, String>(entity, name));
				} else {
					linkPairs.add(new Pair<String, String>(entity, entity));
				}
			}
		}
	}

	public String getPlainText() {
		String text = wikiText.replaceAll("&gt;", ">");
		text = text.replaceAll("&lt;", "<");
		text = text.replaceAll("<ref>.*?</ref>", " ");
		text = text.replaceAll("</?.*?>", " ");
		text = text.replaceAll("\\{\\{.*?\\}\\}", " ");
		text = text.replaceAll("\\[\\[.*?:.*?\\]\\]", " ");
		text = text.replaceAll("\\[\\[(.*?)\\]\\]", "$1");
		text = text.replaceAll("\\s(.*?)\\|(\\w+\\s)", " $2");
		text = text.replaceAll("\\[.*?\\]", " ");
		text = text.replaceAll("\\'+", "");
		return text;
	}

  public InfoBox getInfoBox() {
    //parseInfoBox is expensive. Doing it only once like other parse* methods
    if(infoBox == null)
      infoBox = parseInfoBox();
    return infoBox;
  }

  private InfoBox parseInfoBox() {
    String INFOBOX_CONST_STR = "{{Infobox";
    int startPos = wikiText.indexOf(INFOBOX_CONST_STR);
    if(startPos < 0) return null;
    int bracketCount = 2;
    int endPos = startPos + INFOBOX_CONST_STR.length();
    for(; endPos < wikiText.length(); endPos++) {
      switch(wikiText.charAt(endPos)) {
        case '}':
          bracketCount--;
          break;
        case '{':
          bracketCount++;
          break;
        default:
      }
      if(bracketCount == 0) break;
    }
    if(endPos+1 >= wikiText.length()) return null;
    // This happens due to malformed Infoboxes in wiki text. See Issue #10
    // Giving up parsing is the easier thing to do.
    String infoBoxText = wikiText.substring(startPos, endPos+1);
    infoBoxText = stripCite(infoBoxText); // strip clumsy {{cite}} tags
    // strip any html formatting
    infoBoxText = infoBoxText.replaceAll("&gt;", ">");
    infoBoxText = infoBoxText.replaceAll("&lt;", "<");
    infoBoxText = infoBoxText.replaceAll("<ref.*?>.*?</ref>", " ");
		infoBoxText = infoBoxText.replaceAll("</?.*?>", " ");
    return new InfoBox(infoBoxText);
  }

  private String stripCite(String text) {
    String CITE_CONST_STR = "{{cite";
    int startPos = text.indexOf(CITE_CONST_STR);
    if(startPos < 0) return text;
    int bracketCount = 2;
    int endPos = startPos + CITE_CONST_STR.length();
    for(; endPos < text.length(); endPos++) {
      switch(text.charAt(endPos)) {
        case '}':
          bracketCount--;
          break;
        case '{':
          bracketCount++;
          break;
        default:
      }
      if(bracketCount == 0) break;
    }
    text = text.substring(0, startPos-1) + text.substring(endPos);
    return stripCite(text);   
  }

  public String getTranslatedTitle(String languageCode) {
    Pattern pattern = Pattern.compile("^\\[\\[" + languageCode + ":(.*?)\\]\\]$", Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(wikiText);
    if(matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

}