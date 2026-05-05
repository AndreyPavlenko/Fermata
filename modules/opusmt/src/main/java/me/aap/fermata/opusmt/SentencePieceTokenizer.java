package me.aap.fermata.opusmt;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

class SentencePieceTokenizer {
	private static final char SPACE = '▁';
	private static final int UNK_ID = 3;

	private final String[] idToToken;
	private final HashMap<String, Integer> tokenToId;
	private final float[] scores;
	private final int bosId;
	private final int eosId;

	private SentencePieceTokenizer(String[] idToToken, HashMap<String, Integer> tokenToId,
			float[] scores, int bosId, int eosId) {
		this.idToToken = idToToken;
		this.tokenToId = tokenToId;
		this.scores = scores;
		this.bosId = bosId;
		this.eosId = eosId;
	}

	static SentencePieceTokenizer fromJson(String json) throws Exception {
		var root = new JSONObject(json);
		var model = root.getJSONObject("model");
		var vocabArray = model.getJSONArray("vocab");
		int size = vocabArray.length();
		var tokenToId = new HashMap<String, Integer>(size * 2);
		var scores = new float[size];
		for (int i = 0; i < size; i++) {
			var entry = vocabArray.getJSONArray(i);
			tokenToId.put(entry.getString(0), i);
			scores[i] = (float) entry.getDouble(1);
		}

		int maxId = size - 1;
		int bosId = tokenToId.getOrDefault("<s>", 0);
		int eosId = tokenToId.getOrDefault("</s>", 0);

		if (root.has("added_tokens")) {
			var added = root.getJSONArray("added_tokens");
			for (int i = 0; i < added.length(); i++) {
				var t = added.getJSONObject(i);
				int id = t.getInt("id");
				String tok = t.getString("content");
				tokenToId.put(tok, id);
				if (id > maxId) maxId = id;
				if ("<s>".equals(tok)) bosId = id;
				else if ("</s>".equals(tok)) eosId = id;
			}
		}

		var idToToken = new String[maxId + 1];
		for (var entry : tokenToId.entrySet()) {
			int id = entry.getValue();
			if (id < idToToken.length) idToToken[id] = entry.getKey();
		}

		return new SentencePieceTokenizer(idToToken, tokenToId, scores, bosId, eosId);
	}

	int[] encode(String text, int prefixId) {
		var words = text.trim().split(" ");
		var tokens = new ArrayList<Integer>();
		for (var word : words) encodeWord(SPACE + word, tokens);
		tokens.add(eosId);
		int offset = prefixId >= 0 ? 1 : 0;
		int[] ids = new int[tokens.size() + offset];
		if (offset > 0) ids[0] = prefixId;
		for (int i = 0; i < tokens.size(); i++) ids[i + offset] = tokens.get(i);
		return ids;
	}

	int getTokenId(String token) {
		return tokenToId.getOrDefault(token, -1);
	}

	private void encodeWord(String word, ArrayList<Integer> out) {
		int n = word.length();
		float[] best = new float[n + 1];
		int[] backtrack = new int[n + 1];
		best[0] = 0;
		for (int i = 1; i <= n; i++) best[i] = Float.NEGATIVE_INFINITY;

		for (int i = 0; i < n; i++) {
			if (best[i] == Float.NEGATIVE_INFINITY) continue;
			for (int j = i + 1; j <= n; j++) {
				var id = tokenToId.get(word.substring(i, j));
				if (id != null) {
					float s = best[i] + scores[id];
					if (s > best[j]) {
						best[j] = s;
						backtrack[j] = i;
					}
				}
			}
		}

		if (best[n] == Float.NEGATIVE_INFINITY) {
			for (int i = 0; i < n; i++)
				out.add(tokenToId.getOrDefault(String.valueOf(word.charAt(i)), UNK_ID));
			return;
		}

		int pos = n;
		int start = out.size();
		while (pos > 0) {
			int prev = backtrack[pos];
			out.add(start, tokenToId.getOrDefault(word.substring(prev, pos), UNK_ID));
			pos = prev;
		}
	}

	String decode(int[] ids) {
		var sb = new StringBuilder();
		for (int id : ids) {
			if (id == eosId || id == bosId || id < 0 || id >= idToToken.length) continue;
			var tok = idToToken[id];
			if (tok == null || (tok.charAt(0) == '<' && tok.charAt(tok.length() - 1) == '>')) continue;
			if (tok.charAt(0) == SPACE) {
				if (sb.length() > 0) sb.append(' ');
				sb.append(tok, 1, tok.length());
			} else {
				sb.append(tok);
			}
		}
		return sb.toString().trim();
	}

	int getEosId() { return eosId; }
	int getPadId() { return tokenToId.getOrDefault("<pad>", eosId); }
}