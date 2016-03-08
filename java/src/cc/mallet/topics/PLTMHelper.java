package cc.mallet.topics;

import cc.mallet.types.Alphabet;

/**
 * Mallet polylingual LDA tm (PLTM) Helper. Extra accessors available.
 *
 * @author ljo
 */
public class PLTMHelper {
    
    public static int getTopicBits(PolylingualTopicModel model) {
	return model.topicBits;
    }

    public static int getTopicMask(PolylingualTopicModel model) {
	return model.topicMask;
    }

    public static double[] getAlpha(PolylingualTopicModel model) {
	return model.alpha;
    }

    public static double[] getBetas(PolylingualTopicModel model) {
	return model.betas;
    }

    public static Alphabet[] getAlphabets(PolylingualTopicModel model) {
	return model.alphabets;
    }

    public static int[] getVocabularySizes(PolylingualTopicModel model) {
	return model.vocabularySizes;
    }

    public static int[][][] getLanguageTypeTopicCounts(PolylingualTopicModel model) {
	return model.languageTypeTopicCounts;
    }

    public static int[][] getLanguageTokensPerTopic(PolylingualTopicModel model) {
	return model.languageTokensPerTopic;
    }
}
