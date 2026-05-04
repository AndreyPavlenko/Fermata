package me.aap.fermata.opusmt;

import static java.util.Arrays.copyOf;

import java.io.File;
import java.util.HashMap;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import me.aap.utils.io.IoUtils;

class OpusMtModel {
	private static final int MAX_OUTPUT_LEN = 100;
	private static final float MAX_LEN_FACTOR = 1.5f;
	private static final OrtEnvironment env = OrtEnvironment.getEnvironment();

	private final OrtSession encoder;
	private final OrtSession decoder;
	private final SentencePieceTokenizer srcTok;
	private final SentencePieceTokenizer tgtTok;

	OpusMtModel(File encoder, File decoder, SentencePieceTokenizer srcTok,
							SentencePieceTokenizer tgtTok) throws Exception {
		this.srcTok = srcTok;
		this.tgtTok = tgtTok;
		var opts = new OrtSession.SessionOptions();
		try {opts.addNnapi();} catch (Exception ignored) {}
		this.encoder = env.createSession(encoder.getAbsolutePath(), opts);
		this.decoder = env.createSession(decoder.getAbsolutePath(), opts);
	}

	String translate(String text, int tagId) throws Exception {
		int[] srcIds = srcTok.encode(text, tagId);
		int n = srcIds.length;
		long[] inputIds = new long[n];
		long[] attMask = new long[n];
		for (int i = 0; i < n; i++) {
			inputIds[i] = srcIds[i];
			attMask[i] = 1;
		}

		long[][] inputIdsBatch = {inputIds};
		long[][] attMaskBatch = {attMask};

		OnnxTensor encoderHidden;
		try (var inputIdsTensor = OnnxTensor.createTensor(env, inputIdsBatch);
				 var attMaskTensor = OnnxTensor.createTensor(env, attMaskBatch)) {
			var encoderInputs = new HashMap<String, OnnxTensor>(2);
			encoderInputs.put("input_ids", inputIdsTensor);
			encoderInputs.put("attention_mask", attMaskTensor);
			var result = encoder.run(encoderInputs);
			encoderHidden = (OnnxTensor) result.get(0);
		}

		int eosId = tgtTok.getEosId();
		int padId = tgtTok.getPadId();
		int maxLen = Math.min(MAX_OUTPUT_LEN, (int) (n * MAX_LEN_FACTOR));
		long[] decTokens = new long[maxLen + 1];
		decTokens[0] = padId;
		int decLen = 1;
		int bestId;

		var decoderInputs = new HashMap<String, OnnxTensor>(3);
		decoderInputs.put("encoder_hidden_states", encoderHidden);

		try (var attMaskTensor = OnnxTensor.createTensor(env, attMaskBatch)) {
			decoderInputs.put("encoder_attention_mask", attMaskTensor);
			for (int step = 0; step < maxLen; step++) {
				OnnxTensor logits;
				try (var decInputTensor = OnnxTensor.createTensor(env,
						new long[][]{copyOf(decTokens, decLen)})) {
					decoderInputs.put("input_ids", decInputTensor);
					var result = decoder.run(decoderInputs);
					logits = (OnnxTensor) result.get(0);
				}
				try {
					var buf = logits.getFloatBuffer();
					int vocabSize = (int) logits.getInfo().getShape()[2];
					int offset = (buf.limit() - vocabSize);
					bestId = 0;
					float bestScore = buf.get(offset);
					for (int i = 1; i < vocabSize; i++) {
						float s = buf.get(offset + i);
						if (s > bestScore) {
							bestScore = s;
							bestId = i;
						}
					}
				} finally {
					logits.close();
				}
				if (bestId == eosId) break;
				decTokens[decLen++] = bestId;
			}
		} finally {
			encoderHidden.close();
		}

		int[] ids = new int[decLen - 1];
		for (int i = 1; i < decLen; i++) ids[i - 1] = (int) decTokens[i];
		return tgtTok.decode(ids);
	}

	@Override
	protected void finalize() {
		IoUtils.close(encoder, decoder);
	}
}
