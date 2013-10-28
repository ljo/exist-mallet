package org.exist.xquery.mallet.topicmodeling;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
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
//import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
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
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The default, five, top ranked words per topic")
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
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
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
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The $number-of-words-per-topic top ranked words per topic") 
                              ),
        new FunctionSignature(
                              new QName("topic-model-inference", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes instances and creates a topic model which can be used for inference. Returns the topic probabilities for the inferenced instances.",
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
                                                                    "The lowercase two-letter ISO-639 code"),
                                  new FunctionParameterSequenceType("instances-inference-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to inference topics on")

                              },
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The topic probabilities for the inferenced instances") 
                              ),
        new FunctionSignature(
                              new QName("topic-model-inference", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes new instances and applies the stored topic model's inferencer. Returns the topic probabilities for the inferenced instances.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("topic-model-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized topic model document to use"),
                                  new FunctionParameterSequenceType("instances-inference-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to inference topics on"),
                                  new FunctionParameterSequenceType("number-of-iterations", Type.INTEGER, Cardinality.EXACTLY_ONE,
                                                                    "The number of iterations to run"),

                                  new FunctionParameterSequenceType("thinning", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                                                                    "The value of the thinning parameter, default is 10"),
                                  new FunctionParameterSequenceType("burn-in", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                                                                    "The value of the burn-in parameter, default is 10")

                              },
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The topic probabilities for the inferenced instances")
                              )
    };

    private static File dataDir = null;

    private static String instancesSource = null;
    private static InstanceList cachedInstances = null;

    private static String inferencerSource = null;
    private static TopicInferencer cachedInferencer = null;

    private static String topicModelSource = null;
    private static ParallelTopicModel cachedTopicModel = null;


    public TopicModel(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String instancesPath = null;
        String inferencerInstancesPath = null;
        String topicModelPath = null;
        int numWordsPerTopic = 5;
        int numTopics = 100;
        int numIterations = 50;
        int numThreads = 2;
        double alpha_t = 0.01;
        double beta_w = 0.01;
        Locale locale = Locale.US;
         // thinning = 1, burnIn = 5
        int thinning = 10;
        int burnIn = 10;

        boolean showWordlists = false;
        boolean storeTopicModel = true;
        boolean useStoredTopicModel = isCalledAs("topic-model-inference")
            && getSignature().getArgumentCount() == 5 ? true : false;

        context.pushDocumentContext();

        try {

            if (!useStoredTopicModel) {
                if (!args[0].isEmpty()) {
                    instancesPath = args[0].getStringValue();
                }
                if (getSignature().getArgumentCount() > 1) {
                    if (!args[1].isEmpty()) {
                        numWordsPerTopic = ((NumericValue) args[1].convertTo(Type.INTEGER)).getInt();
                    }
                }
            }
            if (getSignature().getArgumentCount() > 2) {
                if (isCalledAs("topic-model-sample")) {
                    if (!args[2].isEmpty()) {
                        locale = new Locale(args[2].getStringValue());
                    }
                } else if (useStoredTopicModel) {
                    if (!args[0].isEmpty()) {
                        topicModelPath = args[0].getStringValue();
                    }
                    if (!args[1].isEmpty()) {
                        inferencerInstancesPath = args[1].getStringValue();
                    }
                    if (!args[2].isEmpty()) {
                        numIterations = ((NumericValue) args[2].convertTo(Type.INTEGER)).getInt();
                    }
                    if (!args[3].isEmpty()) {
                        thinning = ((NumericValue) args[3].convertTo(Type.INTEGER)).getInt();
                    }
                    if (!args[4].isEmpty()) {
                        burnIn = ((NumericValue) args[4].convertTo(Type.INTEGER)).getInt();
                    }
                } else {
                    if (!args[2].isEmpty()) {
                        numTopics = ((NumericValue) args[2].convertTo(Type.INTEGER)).getInt();
                    }
                    if (!args[3].isEmpty()) {
                        numIterations = ((NumericValue) args[3].convertTo(Type.INTEGER)).getInt();
                    }
                    if (!args[4].isEmpty()) {
                        numThreads = ((NumericValue) args[4].convertTo(Type.INTEGER)).getInt();
                    }
                    if (!args[5].isEmpty()) {
                        alpha_t = ((NumericValue) args[5].convertTo(Type.DOUBLE)).getDouble();
                    }
                    if (!args[6].isEmpty()) {
                        beta_w = ((NumericValue) args[6].convertTo(Type.DOUBLE)).getDouble();
                    }
                    if (!args[7].isEmpty()) {
                        locale = new Locale(args[7].getStringValue());
                    }
                    if (isCalledAs("topic-model-inference")) {
                        if (!args[8].isEmpty()) {
                            inferencerInstancesPath = args[8].getStringValue();;
                        }
                    }
                }
            }
            ParallelTopicModel model = null;
            ValueSequence result = new ValueSequence();
            final String malletLoggingLevel = System.getProperty("java.util.logging.config.level");

            if (!useStoredTopicModel) {
                LOG.debug("Loading instances data.");
                final double alpha_t_param = numTopics * alpha_t;
                model = new ParallelTopicModel(numTopics, alpha_t_param, beta_w);
                if ("".equals(malletLoggingLevel)) {
                    model.logger.setLevel(Level.SEVERE);
                } else {
                    //model.logger.setLevel(malletLoggingLevel);
                    model.logger.setLevel(Level.SEVERE);
                }

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
                
             
                // Estimate the topic distribution of the first instance, 
                //  given the current Gibbs state.
                LOG.info("Estimating topic distribution.");
                double[] topicDistribution = model.getTopicProbabilities(0);
            
                if (storeTopicModel) {
                    if (topicModelPath == null) {
                        topicModelPath = instancesPath + ".tm";
                    }

                    storeTopicModel(model, topicModelPath);
                }

                // Get an array of sorted sets of word ID/count pairs
                ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
                
                // Show top N words in topics with proportions for the first document
                if (!isCalledAs("topic-model-inference")) {
                    result.add(topicXMLReport(context, topicSortedWords, dataAlphabet, numWordsPerTopic, numTopics, alpha_t));
                    
                    if (showWordlists) {
                        // Make wordlists with topics for all instances individually.
                        // And all together even for -sample?
                        result.add(wordListsXMLReport(context, model, dataAlphabet));
                    }
                }
            } else {
                LOG.info("Reading stored topic model for inferencing.");
                model = readTopicModel(context, topicModelPath); 
            }
            if (isCalledAs("topic-model-inference")) {
                LOG.info("Creating inferencer.");
                TopicInferencer inferencer = model.getInferencer();
                result.add(inferencedTopicsXMLReport(context, inferencer, inferencerInstancesPath, numIterations, thinning, burnIn));
            }

            // Create a new instance with high probability of topic 0
            //Formatter out3 = new Formatter(new StringBuilder(), locale);
            //StringBuilder topicZeroText = new StringBuilder();
            //Iterator<IDSorter> iterator = topicSortedWords.get(0).iterator();
            
            //int rank = 0;
            //while (iterator.hasNext() && rank < numWordsPerTopic) {
            //    IDSorter idCountPair = iterator.next();
            //    topicZeroText.append(dataAlphabet.lookupObject(idCountPair.getID()) + " ");
            //    rank++;
            //}
      
            // Create a new instance named "test instance" with empty target and source fields.
            //InstanceList testing = new InstanceList(instances.getPipe());
            //testing.addThruPipe(new Instance(topicZeroText.toString(), null, "test instance", null));
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
                LOG.debug("Reading stored inferencer.");
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

    /**
     * The method <code>readTopicModel</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param topicModelPath a <code>String</code> value
     * @return a <code>ParallelTopicModel</code> value
     * @exception XPathException if an error occurs
     */
    public static ParallelTopicModel readTopicModel(XQueryContext context, final String topicModelPath) throws XPathException {
        try {
            if (topicModelSource == null || !topicModelPath.equals(topicModelSource)) {
                topicModelSource = topicModelPath;
                DocumentImpl doc = (DocumentImpl) context.getBroker().getXMLResource(XmldbURI.createInternal(topicModelPath));
                if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                    throw new XPathException("TopicModel path does not point to a binary resource");
                }
                BinaryDocument binaryDocument = (BinaryDocument)doc;
                File topicModelFile = context.getBroker().getBinaryFile(binaryDocument);
                if (dataDir == null) {
                    dataDir = topicModelFile.getParentFile();
                }
                LOG.debug("Reading stored topic model.");
                cachedTopicModel = ParallelTopicModel.read(topicModelFile);
            }
        } catch (PermissionDeniedException e) {
            throw new XPathException("Permission denied to read topicModel resource", e);
        } catch (IOException e) {
            throw new XPathException("Error while reading topicModel resource: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new XPathException("Exception while reading topicModel resource", e);
        }

        return cachedTopicModel;
    }

    private void storeTopicModel(final ParallelTopicModel model, final String topicModelPath)  throws XPathException {
        XmldbURI sourcePath = XmldbURI.createInternal(topicModelPath);
        XmldbURI colURI = sourcePath.removeLastSegment();
        XmldbURI docURI = sourcePath.lastSegment();
        // References to the database
        BrokerPool brokerPool = context.getBroker().getBrokerPool();
        DBBroker broker = null;
        final org.exist.security.SecurityManager sm = brokerPool.getSecurityManager();
        Collection collection = null;

        // Start transaction
        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();
        
        try {
            broker = brokerPool.get(sm.getCurrentSubject());
        
            collection = broker.openCollection(colURI, Lock.WRITE_LOCK);
            if (collection == null) {
                String errorMessage = String.format("Collection %s does not exist", colURI);
                LOG.error(errorMessage);
                txnManager.abort(txn);
                throw new XPathException(this, errorMessage);
            }


            // Stream into database

            File topicModelTempFile = File.createTempFile("malletTopicModel", ".tmp");
            topicModelTempFile.deleteOnExit();
            model.write(topicModelTempFile);
            VirtualTempFile vtf = new VirtualTempFile(topicModelTempFile);
            InputStream bis = vtf.getByteStream();
            
            try {
                DocumentImpl doc = collection.addBinaryResource(txn, broker, docURI, bis, MimeType.BINARY_TYPE.getName(), vtf.length());
            } finally {
                bis.close();
            }
            // Commit change
            txnManager.commit(txn);
            
        } catch (Throwable ex) {
            txnManager.abort(txn);
            throw new XPathException(this, String.format("Unable to write instances document into database: %s", ex.getMessage()));

        } finally {
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }
            txnManager.close(txn);
            brokerPool.release(broker);
        }
    }


	/**
     * The method <code>topicXMLReport</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param topicSortedWords an <code>ArrayList<TreeSet<IDSorter>></code> value
     * @param dataAlphabet an <code>Alphabet</code> value
     * @param numWordsPerTopic an <code>int</code> value
     * @param numTopics an <code>int</code> value
     * @param alpha_t a <code>double</code> value
     * @return a <code>NodeValue</code> value
     */
    public NodeValue topicXMLReport(final XQueryContext context, final ArrayList<TreeSet<IDSorter>> topicSortedWords, Alphabet dataAlphabet, final int numWordsPerTopic, final int numTopics, final double alpha_t) {
        double[] alpha = new double[numTopics];
        Arrays.fill(alpha, alpha_t);
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("topicModel", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
		for (int topic = 0; topic < numTopics; topic++) {
            builder.startElement(new QName("topic", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
            builder.addAttribute(new QName("n", null, null), String.valueOf(topic));
            builder.addAttribute(new QName("alpha", null, null), String.valueOf(alpha[topic]));
            builder.addAttribute(new QName("totalTokens", null, null), String.valueOf(topicSortedWords.get(topic).size()));
			int word = 0;
			Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
			while (iterator.hasNext() && word < numWordsPerTopic) {
				IDSorter info = iterator.next();
                builder.startElement(new QName("token", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
                builder.addAttribute(new QName("rank", null, null), String.valueOf(word + 1));
                builder.characters((CharSequence) dataAlphabet.lookupObject(info.getID()));
                builder.endElement();
                word++;
			}
            builder.endElement();
		}
        builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
	}


	/**
     * The method <code>topicXMLReport</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param model a <code>ParallelTopicModel</code> value
     * @param dataAlphabet an <code>Alphabet</code> value
     * @return a <code>NodeValue</code> value
     */
    public NodeValue wordListsXMLReport(final XQueryContext context, final ParallelTopicModel model, final Alphabet dataAlphabet) {
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("wordLists", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
        for (int i = 0; i < model.getData().size(); i++) {
            FeatureSequence tokens = (FeatureSequence) model.getData().get(i).instance.getData();
            builder.startElement(new QName("wordList", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
                builder.addAttribute(new QName("n", null, null), String.valueOf(i));
            LabelSequence topics = model.getData().get(i).topicSequence;
            for (int position = 0; position < tokens.getLength(); position++) {
                builder.startElement(new QName("token", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
                builder.addAttribute(new QName("normalized-form", null, null), String.valueOf(dataAlphabet.lookupObject(tokens.getIndexAtPosition(position))));
                builder.addAttribute(new QName("topic", null, null), String.valueOf(topics.getIndexAtPosition(position)));
                builder.endElement();
            }
            builder.endElement();
        }
        builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
    }

    /**
     * The method <code>inferencedTopicsXMLReport</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param inferencer a <code>TopicInferencer</code> value
     * @param inferencerInstancesPath a <code>String</code> value
     * @param numIterations an <code>int</code> value
     * @param thinning an <code>int</code> value
     * @param burnIn an <code>int</code> value
     * @return a <code>NodeValue</code> value
     * @exception XPathException if an error occurs
     */
    public NodeValue inferencedTopicsXMLReport(final XQueryContext context, final TopicInferencer inferencer, final String inferencerInstancesPath, final int numIterations, final int thinning, final int burnIn) throws XPathException {
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("inferencedTopics", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
        LOG.info("Sampling distribution.");
        InstanceList inferenceInstances = readInstances(context, inferencerInstancesPath);
        //public double[] getSampledDistribution(Instance instance,
        //                           int numIterations,
        //                           int thinning,
        //                           int burnIn)
        for (int ii = 0; ii < inferenceInstances.size(); ii++) {
            builder.startElement(new QName("instance", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
            builder.addAttribute(new QName("n", null, null), String.valueOf(ii));
            
            double[] testProbabilities = inferencer.getSampledDistribution(inferenceInstances.get(0), numIterations, thinning, burnIn);
            for (int tp = 0; tp < testProbabilities.length; tp++) {
                builder.startElement(new QName("topic", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
                builder.addAttribute(new QName("n", null, null), String.valueOf(tp));
                builder.addAttribute(new QName("probability", null, null), String.valueOf(testProbabilities[tp]));
                builder.endElement();
            }
            builder.endElement();
        }
        builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
    }

}
