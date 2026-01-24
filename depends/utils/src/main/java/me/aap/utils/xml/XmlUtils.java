package me.aap.utils.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import me.aap.utils.io.ByteBufferInputStream;
import me.aap.utils.log.Log;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Andrey Pavlenko
 */
public class XmlUtils {

	public static Document readXml(ByteBuffer xml) throws ParserConfigurationException,
			IOException, SAXException {
		return readXml(new ByteBufferInputStream(xml));
	}

	public static Document readXml(InputStream in) throws ParserConfigurationException,
			IOException, SAXException {
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		f.setExpandEntityReferences(true);
		DocumentBuilder b = f.newDocumentBuilder();
		return b.parse(in);
	}

	public static void writeXml(Node node, OutputStream out) throws TransformerException, IOException {
		Writer w = new OutputStreamWriter(out, UTF_8);
		writeXml(node, w);
		w.flush();
	}

	public static void writeXml(Node node, Writer w) throws TransformerException {
		writeXml(node, w, false);
	}

	public static void writeXml(Node node, Writer w, boolean omitXmlDecl) throws TransformerException {
		Transformer t = TransformerFactory.newInstance().newTransformer();
		t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		t.setOutputProperty(OutputKeys.INDENT, "yes");
		if (omitXmlDecl) t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		t.transform(new DOMSource(node), new StreamResult(w));
	}

	public static Element findChild(Node n, String name) {
		NodeList list = n.getChildNodes();
		int count = list.getLength();

		for (int i = 0; i < count; i++) {
			Node c = list.item(i);

			if (c instanceof Element) {
				if (c.getLocalName().equals(name)) {
					return (Element) c;
				}
			}
		}

		return null;
	}

	public static String nodeToString(Node node) {
		StringWriter sw = new StringWriter();

		try {
			writeXml(node, sw, true);
		} catch (Exception ex) {
			Log.e(ex, "nodeToString() failed");
		}

		return sw.toString();
	}
}
