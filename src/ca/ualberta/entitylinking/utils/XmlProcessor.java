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
package ca.ualberta.entitylinking.utils;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlProcessor {
	public XmlProcessor() {
		
	}
	
	public static String getTextValue(Element ele, String tagName) {
		String textVal = null;
		NodeList nl = ele.getElementsByTagName(tagName);
		if (nl != null && nl.getLength() > 0) {
			Element element = (Element) nl.item(0);
			if (element.hasChildNodes())
				textVal = element.getFirstChild().getNodeValue();
		}
		
		return textVal;
	}
	
	public static int getIntValue(Element ele, String tagName) {
		return Integer.parseInt(getTextValue(ele, tagName));
	}
	
	public static String getAttribute(Element ele, String tagName, String attrName) {
		String value = null;
		NodeList nl = ele.getElementsByTagName(tagName);
		if (nl != null && nl.getLength() > 0) {
			Element element = (Element) nl.item(0);
			if (element.hasAttribute(attrName))
				value = element.getAttribute(attrName);
		}
		
		return value;
	}
	
	public static String getTextValue(Element ele, String tagName, String attrName, String attrValue) {
		NodeList nl = ele.getElementsByTagName(tagName);
		if (nl == null || nl.getLength() <= 0)
			return null;

		for (int i = 0; i < nl.getLength(); i++) {
			Element element = (Element) nl.item(i);
			if (element.hasAttribute(attrName)) {
				String value = element.getAttribute(attrName);
				if (value.equalsIgnoreCase(attrValue))
					return element.getTextContent();
			}
		}
		
		return null;
	}
	
}
