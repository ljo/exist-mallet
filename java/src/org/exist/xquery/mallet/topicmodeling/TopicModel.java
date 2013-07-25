package org.exist.xquery.mallet.topicmodeling;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.*;

import org.apache.log4j.Logger;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.topics.*;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelSequence;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.VirtualTempFile;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

/**
 * Create Instances functions to be used by most module functions of the Mallet sub-packages.
 *
 * @author ljo
 */
public class TopicModel extends BasicFunction {
    private final static Logger LOG = Logger.getLogger(TopicModel.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                              new QName("topic-model-sample", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic. All other parameters use default values. Runs the model for 50 iterations and stops (this is for testing only, for real applications, use 1000 to 2000 iterations).",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to use")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE,
                                                             "The $number-of-words-per-topic top ranked words per topic")
                              ),
        new FunctionSignature(
                              new QName("topic-model-sample", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic. All other parameters use default values. Runs the model for 50 iterations and stops (this is for testing only, for real applications, use 1000 to 2000 iterations).",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to use"),
                                  new FunctionParameterSequenceType("number-of-words-per-topic", Type.INTEGER, Cardinality.EXACTLY_ONE,
                                                                    "The number of top ranked words per topic to show"),
                                  new FunctionParameterSequenceType("language", Type.STRING, Cardinality.ZERO_OR_ONE,
                                                                    "The lowercase two-letter ISO-639 code")

                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE,
                                                             "The $number-of-words-per-topic top ranked words per topic")
                              ),
        new FunctionSignature(
                              new QName("topic-model", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to use"),
                                  new FunctionParameterSequenceType("number-of-words-per-topic", Type.INTEGER, Cardinality.EXACTLY_ONE,
                                                                    "The number of top ranked words per topic to show"),
                                  new FunctionParameterSequenceType("number-of-topics", Type.INTEGER, Cardinality.EXACTLY_ONE,
                                                                    "The number of topics to create"),
                                  new FunctionParameterSequenceType("number-of-iterations", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                                                                    "The number of iterations to run"),
                                  new FunctionParameterSequenceType("number-of-threads", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                                                                    "The number of threads to use"),
                                  new FunctionParameterSequenceType("alpha_t", Type.DOUBLE, Cardinality.ZERO_OR_ONE,
                                                                    "The value for the Dirichlet alpha_t parameter"),
                                  new FunctionParameterSequenceType("beta_w", Type.DOUBLE, Cardinality.ZERO_OR_ONE,
                                                                    "The value for the Prior beta_w parameter"),
                                  new FunctionParameterSequenceType("language", Type.STRING, Cardinality.ZERO_OR_ONE,
                                                                    "The lowercase two-letter ISO-639 code")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE_OR_MORE,
                                                             "The $number-of-words-per-topic top ranked words per topic")
                              )
    };

    private static File dataDir = null;

    private static String instancesSource = null;
    private static InstanceList cachedInstances = null;

    private static String inferencerSource = null;
    private static TopicInferencer cachedInferencer = null;

    public TopicModel(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String instancesPath = "/db/apps/mallet-topic-modeling-0.1/resources/instances/topic-example.mallet";
        int numWordsPerTopic = 5;
        int numTopics = 100;
        int numIterations = 50;
        int numThreads = 2;
        double alpha_t = 0.01;
        double beta_w = 0.01;
        Locale locale = Locale.US;
        
        context.pushDocumentContext();

        try {
            if (isCalledAs("topic-model-sample")) {
                if (!args[0].isEmpty()) {
                    instancesPath = args[0].getStringValue();
                }
                if (args[1] != null && !args[1].isEmpty()) {
                    numWordsPerTopic = ((NumericValue) args[1].convertTo(Type.INTEGER).itemAt(0).convertTo(Type.INTEGER)).getInt();
                }
            } else {
                if (!args[2].isEmpty()) {
                    numTopics = ((NumericValue) args[2].convertTo(Type.INTEGER).itemAt(0).convertTo(Type.INTEGER)).getInt();
                }
                if (!args[3].isEmpty()) {
                    numIterations = ((NumericValue) args[3].convertTo(Type.INTEGER).itemAt(0).convertTo(Type.INTEGER)).getInt();
                }
                if (!args[4].isEmpty()) {
                    numThreads = ((NumericValue) args[4].convertTo(Type.INTEGER).itemAt(0).convertTo(Type.INTEGER)).getInt();
                }
                if (!args[5].isEmpty()) {
                    alpha_t = ((NumericValue) args[5].convertTo(Type.DOUBLE).itemAt(0).convertTo(Type.DOUBLE)).getDouble();
                }
                if (!args[6].isEmpty()) {
                    beta_w = ((NumericValue) args[6].convertTo(Type.DOUBLE).itemAt(0).convertTo(Type.DOUBLE)).getDouble();
                }
                if (!args[7].getStringValue().isEmpty()) {
                    locale = new Locale(args[7].getStringValue());
                }
            }
            LOG.debug("Loading instances data.");
            final double alpha_t_param = numTopics * alpha_t;
            ParallelTopicModel model = new ParallelTopicModel(numTopics, alpha_t_param, beta_w);
            InstanceList instances = readInstances(context, instancesPath);
            model.addInstances(instances);
            
            // Use N parallel samplers, which each look at one half the corpus and combine
            //  statistics after every iteration.
            model.setNumThreads(numThreads);
            
            // Run the model for 50 iterations by default and stop 
            // (this is for testing only, 
            //  for real applications, use 1000 to 2000 iterations)
            model.setNumIterations(numIterations);
            try {
                LOG.info("Estimating model.");
                model.estimate();
            } catch (IOException e) {
                throw new XPathException(this, "Error while reading instances resource: " + e.getMessage(), e);
            }
            
            // The data alphabet maps word IDs to strings
            Alphabet dataAlphabet = instances.getDataAlphabet();

            ValueSequence result = new ValueSequence();

            // Make wordlists with topics for all instances individually.
            // And all together even for -sample?
            for (int i = 0; i < model.getData().size(); i++) {
                FeatureSequence tokens = (FeatureSequence) model.getData().get(i).instance.getData();
                Formatter out1 = new Formatter(new StringBuilder(), locale);
            
                LabelSequence topics = model.getData().get(i).topicSequence;
                for (int position = 0; position < tokens.getLength(); position++) {
                    out1.format("%s - %d\n", dataAlphabet.lookupObject(tokens.getIndexAtPosition(position)), topics.getIndexAtPosition(position));
                }
                result.add(new StringValue(out1.toString()));
            }
            
            // Estimate the topic distribution of the first instance, 
            //  given the current Gibbs state.
            LOG.info("Estimating topic distribution.");
            double[] topicDistribution = model.getTopicProbabilities(0);
            
            // Get an array of sorted sets of word ID/count pairs
            ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
            
            // Show top N words in topics with proportions for the first document
            Formatter out2 = new Formatter(new StringBuilder(), locale);
            for (int topic = 0; topic < numTopics; topic++) {
                Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
                out2.format("%d\t%.3f\t", topic, topicDistribution[topic]);
                int rank = 0;
                while (iterator.hasNext() && rank < numWordsPerTopic) {
                    IDSorter idCountPair = iterator.next();
                    out2.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
                    rank++;
                }
                out2.format("\n");
            }
            result.add(new StringValue(out2.toString()));

            Formatter out3 = new Formatter(new StringBuilder(), locale);

            // Create a new instance with high probability of topic 0
            StringBuilder topicZeroText = new StringBuilder();
            Iterator<IDSorter> iterator = topicSortedWords.get(0).iterator();
            
            int rank = 0;
            while (iterator.hasNext() && rank < numWordsPerTopic) {
                IDSorter idCountPair = iterator.next();
                topicZeroText.append(dataAlphabet.lookupObject(idCountPair.getID()) + " ");
                rank++;
            }
            
            // Create a new instance named "test instance" with empty target and source fields.
            InstanceList testing = new InstanceList(instances.getPipe());
            //testing.addThruPipe(new Instance(topicZeroText.toString(), null, "test instance", null));

            LOG.info("Creating inferencer.");
            TopicInferencer inferencer = model.getInferencer();
            LOG.info("Sampling distribution.");
            //public double[] getSampledDistribution(Instance instance,
            //                           int numIterations,
            //                           int thinning,
            //                           int burnIn)
            // double[] testProbabilities = inferencer.getSampledDistribution(testing.get(0), 10, 1, 5);
            // out3.format("0\t%.3f", testProbabilities[0]);

            //result.add(new StringValue(out3.toString()));

            return result;

        } finally {
            context.popDocumentContext();
        }
    }

    private void cleanCaches() {
        cachedInstances = null;
        cachedInferencer = null;
    }

    /**
     * The method <code>readInstances</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param instancesPath a <code>String</code> value
     * @return an <code>InstanceList</code> value
     * @exception XPathException if an error occurs
     */
    public static InstanceList readInstances(XQueryContext context, final String instancesPath) throws XPathException {
        try {
            if (instancesSource == null || !instancesPath.equals(instancesSource)) {
                instancesSource = instancesPath;
                DocumentImpl doc = (DocumentImpl) context.getBroker().getXMLResource(XmldbURI.createInternal(instancesPath));
                if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                    throw new XPathException("Instances path does not point to a binary resource");
                }
                BinaryDocument binaryDocument = (BinaryDocument)doc;
                File instancesFile = context.getBroker().getBinaryFile(binaryDocument);
                if (dataDir == null) {
                    dataDir = instancesFile.getParentFile();
                }
               cachedInstances = InstanceList.load(instancesFile);
            }
        } catch (PermissionDeniedException e) {
            throw new XPathException("Permission denied to read instances resource", e);
        } catch (IOException e) {
            throw new XPathException("Error while reading instances resource: " + e.getMessage(), e);
        }
        return cachedInstances;
    }

    /**
     * The method <code>readInferencer</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param inferencerPath a <code>String</code> value
     * @return an <code>TopicInferencer</code> value
     * @exception XPathException if an error occurs
     */
    public static TopicInferencer readInferencer(XQueryContext context, final String inferencerPath) throws XPathException {
        try {
            if (inferencerSource == null || !inferencerPath.equals(inferencerSource)) {
                inferencerSource = inferencerPath;
                DocumentImpl doc = (DocumentImpl) context.getBroker().getXMLResource(XmldbURI.createInternal(inferencerPath));
                if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                    throw new XPathException("Inferencer path does not point to a binary resource");
                }
                BinaryDocument binaryDocument = (BinaryDocument)doc;
                File inferencerFile = context.getBroker().getBinaryFile(binaryDocument);
                if (dataDir == null) {
                    dataDir = inferencerFile.getParentFile();
                }
                
                cachedInferencer = TopicInferencer.read(inferencerFile);
            }
        } catch (PermissionDeniedException e) {
            throw new XPathException("Permission denied to read inferencer resource", e);
        } catch (IOException e) {
            throw new XPathException("Error while reading inferencer resource: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new XPathException("Exception while reading inferencer resource", e);
        }

        return cachedInferencer;
    }
}
