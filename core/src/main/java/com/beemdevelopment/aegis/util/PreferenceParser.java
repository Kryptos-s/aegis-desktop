package com.beemdevelopment.aegis.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parses an Android {@code SharedPreferences} XML file, for the FreeOTP, FreeOTP+ and Authy
 * importers. The Android app uses {@code XmlPullParser}; this uses StAX.
 */
public class PreferenceParser {
    private PreferenceParser() {

    }

    /** These files come from outside, so the reader resolves no external entities and no DTDs. */
    public static XMLStreamReader createReader(InputStream stream) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return factory.createXMLStreamReader(stream);
    }

    public static List<XmlEntry> parse(InputStream stream) throws IOException, XMLStreamException {
        XMLStreamReader reader = createReader(stream);
        try {
            return parse(reader);
        } finally {
            reader.close();
        }
    }

    public static List<XmlEntry> parse(XMLStreamReader parser) throws IOException, XMLStreamException {
        List<XmlEntry> entries = new ArrayList<>();

        while (parser.hasNext() && parser.getEventType() != XMLStreamConstants.START_ELEMENT) {
            parser.next();
        }
        require(parser, XMLStreamConstants.START_ELEMENT, "map");

        int depth = 0;
        while (parser.hasNext()) {
            int event = parser.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == 0) {
                    break;
                }
                depth--;
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            if (!parser.getLocalName().equals("string")) {
                skip(parser);
                continue;
            }

            entries.add(parseEntry(parser));
        }

        return entries;
    }

    private static XmlEntry parseEntry(XMLStreamReader parser) throws XMLStreamException {
        require(parser, XMLStreamConstants.START_ELEMENT, "string");
        String name = parser.getAttributeValue(null, "name");
        String value = parseText(parser);
        require(parser, XMLStreamConstants.END_ELEMENT, "string");

        XmlEntry entry = new XmlEntry();
        entry.Name = name;
        entry.Value = value;
        return entry;
    }

    private static String parseText(XMLStreamReader parser) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        while (parser.hasNext()) {
            int event = parser.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                text.append(parser.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                skip(parser);
            }
        }
        return text.toString();
    }

    private static void skip(XMLStreamReader parser) throws XMLStreamException {
        if (parser.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException();
        }

        int depth = 1;
        while (depth != 0 && parser.hasNext()) {
            switch (parser.next()) {
                case XMLStreamConstants.END_ELEMENT:
                    depth--;
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    depth++;
                    break;
                default:
                    break;
            }
        }
    }

    private static void require(XMLStreamReader parser, int type, String name) throws XMLStreamException {
        if (parser.getEventType() != type
                || (name != null && !name.equals(parser.getLocalName()))) {
            throw new XMLStreamException(String.format(
                    "Expected %s <%s>, found event %d", type == XMLStreamConstants.START_ELEMENT ? "start" : "end",
                    name, parser.getEventType()));
        }
    }

    public static class XmlEntry {
        public String Name;
        public String Value;
    }
}
