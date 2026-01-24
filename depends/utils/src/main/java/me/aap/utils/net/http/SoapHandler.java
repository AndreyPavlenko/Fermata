package me.aap.utils.net.http;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.ByteBuffer;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.NetChannel;
import me.aap.utils.net.http.HttpError.BadRequest;
import me.aap.utils.net.http.HttpError.ServerError;

import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.net.http.HttpHeader.ACCEPT_RANGES;
import static me.aap.utils.net.http.HttpHeader.CONTENT_TYPE;
import static me.aap.utils.xml.XmlUtils.findChild;
import static me.aap.utils.xml.XmlUtils.nodeToString;
import static me.aap.utils.xml.XmlUtils.readXml;
import static me.aap.utils.xml.XmlUtils.writeXml;

/**
 * @author Andrey Pavlenko
 */
public class SoapHandler implements HttpRequestHandler {
	public static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
	protected final Map<String, MessageHandler> handlers;
	protected final String logTag;
	protected final DocumentBuilder docBuilder;
	private int maxLen = 256;

	public SoapHandler(Map<String, MessageHandler> handlers, String logTag) {
		this.handlers = handlers;
		this.logTag = logTag;

		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();

		try {
			docBuilder = f.newDocumentBuilder();
		} catch (ParserConfigurationException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public FutureSupplier<Void> handleRequest(HttpRequest req) {
		NetChannel channel = req.getChannel();

		if (req.getMethod() != HttpMethod.POST) {
			Log.e("Unexpected request method: ", req.getMethod());
			return BadRequest.instance.write(channel);
		}

		HttpVersion version = req.getVersion();
		return req.getPayload((payload, err) -> handleMessage(channel, version, payload, err));
	}

	private FutureSupplier<Void> handleMessage(NetChannel channel, HttpVersion version,
																						 ByteBuffer payload, Throwable err) {
		if (err != null) {
			Log.e(err, "Failed to get payload");
			channel.close();
			return failed(err);
		}

		if (!payload.hasRemaining()) {
			Log.e("Request payload is empty");
			return BadRequest.instance.write(channel);
		}

		Document doc;

		try {
			doc = readXml(payload);
		} catch (Exception ex) {
			Log.e(ex, "Failed to parse request message");
			return BadRequest.instance.write(channel);
		}

		Log.d("Handling request:\n", nodeToString(doc));

		Element envelope = findChild(doc, "Envelope");

		if (envelope == null) {
			Log.e("No <Envelope> element");
			return BadRequest.instance.write(channel);
		}

		Element body = findChild(envelope, "Body");

		if (body == null) {
			Log.e("No <Body> element");
			return BadRequest.instance.write(channel);
		}

		if (body.getFirstChild() == null) {
			Log.e("<Body> element is empty");
			return BadRequest.instance.write(channel);
		}

		try {
			return handleMessage(channel, version, doc, body);
		} catch (Exception ex) {
			Log.e(ex, "Failed to handle message");
			return ServerError.instance.write(channel);
		}
	}

	private FutureSupplier<Void> handleMessage(NetChannel channel, HttpVersion version, Document reqDoc, Element reqBody) throws Exception {
		Document respDoc = docBuilder.newDocument();
		Element envelope = respDoc.createElementNS(SOAP_NS, "s:Envelope");
		Element respBody = respDoc.createElementNS(SOAP_NS, "s:Body");
		respDoc.appendChild(envelope);
		envelope.appendChild(respBody);

		String handlerName = reqBody.getFirstChild().getLocalName();
		MessageHandler h = getHandler(handlerName);
		FutureSupplier<Void> result;

		if (h == null) {
			addFault(respDoc, respBody, "No such handler: " + handlerName, null);
			result = completedVoid();
		} else {
			result = h.handle(reqDoc, reqBody, respDoc, respBody);
			try {
				h.handle(reqDoc, reqBody, respDoc, respBody);
			} catch (Throwable ex) {
				addFault(respDoc, respBody, "Handler failed: " + handlerName, ex);
			}
		}

		return result.onCompletion((r, err) -> {
			if (err != null) addFault(respDoc, respBody, "Handler failed: " + handlerName, err);
			HttpMessageBuilder b = new HttpMessageBuilder(maxLen);
			b.setStatusOk(version);
			b.addHeader(ACCEPT_RANGES);
			b.addHeader(CONTENT_TYPE, "text/xml; charset=\"utf-8\"");

			try {
				ByteBuffer[] resp = b.build(os -> writeXml(respDoc, os));
				maxLen = Math.max(maxLen, resp[resp.length - 1].remaining());
//				Log.d("Sending response:\n", new String(resp.array(), resp.arrayOffset(), resp.remaining(), UTF_8));
				channel.write(resp);
			} catch (Exception ex) {
				Log.e(ex, "Failed to write XML response");
				channel.close();
			}
		});
	}

	private void addFault(Document respDoc, Element respBody, String msg, Throwable ex) {
		Log.w(ex, "Failed to handle message: ", msg);
		Element fault = respDoc.createElementNS(SOAP_NS, "s:Fault");
		Element faultcode = respDoc.createElementNS(SOAP_NS, "s:faultcode");
		Element faultstring = respDoc.createElementNS(SOAP_NS, "s:faultstring");
		faultcode.setTextContent("Server");
		faultstring.setTextContent(msg);
		respBody.appendChild(fault);
		fault.appendChild(faultcode);
		fault.appendChild(faultstring);
	}

	protected MessageHandler getHandler(String name) {
		return handlers.get(name);
	}

	public interface MessageHandler {
		FutureSupplier<Void> handle(Document reqDoc, Element reqBody, Document respDoc, Element respBody);
	}
}