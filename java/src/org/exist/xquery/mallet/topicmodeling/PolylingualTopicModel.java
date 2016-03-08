package org.exist.xquery.mallet.topicmodeling;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.*;

import org.apache.log4j.Logger;
//import org.apache.logging.log4j.Logger;
//import org.apache.logging.log4j.LogManager;

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
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
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
 * Mallet polylingual LDA tm (PLTM).
 *
 * @author ljo
 */
public class PolylingualTopicModel extends BasicFunction {
    private final static Logger LOG = Logger.getLogger(PolylingualTopicModel.class);
    //private final static Logger LOG = LogManager.getLogger(PolylingualTopicModel.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                              new QName("polylingual-topic-model-sample", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic. All other parameters use default values. Runs the model for 50 iterations and stops (this is for testing only, for real applications, use 1000 to 2000 iterations).",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to use")
                              },
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The default, five, top ranked words per topic")
                              ),
        new FunctionSignature(
                              new QName("polylingual-topic-model-sample", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic. All other parameters use default values. Runs the model for 50 iterations and stops (this is for testing only, for real applications, use 1000 to 2000 iterations).",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to use"),
                                  new FunctionParameterSequenceType("number-of-words-per-topic", Type.INTEGER, Cardinality.EXACTLY_ONE,
                                                                    "The number of top ranked words per topic to show"),
                                  new FunctionParameterSequenceType("languages", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "A sequence of lowercase two-letter ISO-639 codes")

                              },
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The $number-of-words-per-topic top ranked words per topic")
                              ),
        new FunctionSignature(
                              new QName("polylingual-topic-model", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
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
                                                                    "The value for the smoothing over topic distribution"),
                                  new FunctionParameterSequenceType("beta_w", Type.DOUBLE, Cardinality.ZERO_OR_ONE,
                                                                    "The value for the smoothing over unigram distribution"),
                                  new FunctionParameterSequenceType("languages", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "A sequence of lowercase two-letter ISO-639 codes")
                              },
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The $number-of-words-per-topic top ranked words per topic") 
                              ),
        new FunctionSignature(
                              new QName("polylingual-topic-model-inference", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes instances and creates a polylingual topic model which can be used for inferencing. Returns the topic probabilities for the inferenced instances.",
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
                                                                    "The value for the smoothing over topic distribution"),
                                  new FunctionParameterSequenceType("beta_w", Type.DOUBLE, Cardinality.ZERO_OR_ONE,
                                                                    "The value for the smooting over unigram distribution"),
                                  new FunctionParameterSequenceType("languages", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "A sequence of lowercase two-letter ISO-639 codes"),
                                  new FunctionParameterSequenceType("instances-inference-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to the serialized instances document to inference topics on")

                              },
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The topic probabilities for the inferenced instances") 
                              ),
        new FunctionSignature(
                              new QName("polylingual-topic-model-inference", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes new instances and applies the stored polylingual topic model's inferencers. Returns the topic probabilities for the inferenced instances.",
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
                                                                    "The value of the burn-in parameter, default is 10"),
                                  new FunctionParameterSequenceType("languages", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "A sequence of lowercase two-letter ISO-639 codes")

                              },
                              new FunctionReturnSequenceType(Type.NODE, Cardinality.ONE_OR_MORE,
                                                             "The topic probabilities for the inferenced instances")
                              )
	// new FunctionParameterSequenceType("configuration", Type.ELEMENT, Cardinality.EXACTLY_ONE,
	// "The configuration, eg &lt;parameters&gt;&lt;param name='useStored' value='false'/&gt;&lt;param name='showWordLists' value='false'/&gt;&lt;/parameters&gt;.")
    };

    private static File dataDir = null;

    private static String topicModelSource = null;
    private static cc.mallet.topics.PolylingualTopicModel cachedTopicModel = null;

    private NumberFormat numberFormatter = null;

    public PolylingualTopicModel(XQueryContext context, FunctionSignature signature) {
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
	double threshold = 0.0;
        //double alpha_t = 0.01;
	//The suggested default 50 works badly, estimation fails in several ways.
	//double alpha_t = 50.0; 
	double alpha_t = 10.0;
        double beta_w = 0.01;
        Locale locale = Locale.US;
        numberFormatter = NumberFormat.getInstance(locale);
        Boolean useNumberFormat = true;
	List<String> languages = new ArrayList<String>();
         // thinning = 1, burnIn = 5
        int thinning = 10;
        int burnIn = 10; // 200 ?

        boolean showWordLists = true;
        boolean storeTopicModel = true;
        boolean useStoredTopicModel = isCalledAs("polylingual-topic-model-inference")
            && getSignature().getArgumentCount() == 6 ? true : false;

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
                if (isCalledAs("polylingual-topic-model-sample")) {
                    if (!args[2].isEmpty()) {
			languages = CreateInstances.getParameterValues(args[2]);
                        locale = new Locale(languages.get(0));
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
                    if (!args[5].isEmpty()) {
			languages = CreateInstances.getParameterValues(args[5]);
                        locale = new Locale(languages.get(0));
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
			languages = CreateInstances.getParameterValues(args[7]);
                        locale = new Locale(languages.get(0));
                    }
                    if (isCalledAs("polylingual-topic-model-inference")) {
                        if (!args[8].isEmpty()) {
                            inferencerInstancesPath = args[8].getStringValue();;
                        }
                    }
                }
            }
            cc.mallet.topics.PolylingualTopicModel model = null;
            ValueSequence result = new ValueSequence();
            final String malletLoggingLevel = System.getProperty("java.util.logging.config.level");
            numberFormatter = NumberFormat.getInstance(locale);
            numberFormatter.setMinimumFractionDigits(3);
            numberFormatter.setMaximumFractionDigits(3);
            

            if (!useStoredTopicModel) {
                LOG.debug("Loading instances data.");
                //final double alpha_t_param = numTopics * alpha_t;
                //model = new PolylingualTopicModel(numTopics, alpha_t_param, beta_w);
		model = new cc.mallet.topics.PolylingualTopicModel(numTopics, alpha_t);
                //if ("".equals(malletLoggingLevel)) {
                //    model.logger.setLevel(Level.SEVERE);
                //} else {
                //    //model.logger.setLevel(malletLoggingLevel);
                //    model.logger.setLevel(Level.SEVERE);
                //}
		try {

		    InstanceList[] instances = new InstanceList[languages.size()];
		    for (int i = 0; i < languages.size(); i++) { 
			instances[i] = readInstances(context, instancesPath, languages.get(i));
		    }

		    model.addInstances(instances);
                
		    // Use N parallel samplers, which each look at one half the corpus and combine
		    //  statistics after every iteration.
		    //model.setNumThreads(numThreads);

		    // new for poly
		    //model.setTopicDisplay(showTopicsInterval, topWords);

		    // Run the model for 50 iterations by default and stop
		    // (this is for testing only,
		    //  for real applications, use 1000 to 2000 iterations)
		    model.setNumIterations(numIterations);

		    // new for poly
		    //model.setOptimizeInterval(optimizeInterval);
		    model.setBurninPeriod(burnIn);

		    LOG.info("Estimating model.");
		    model.estimate();
		    LOG.info("Estimating model, done.");

		    // Estimate the topic distribution of the first instance,
		    //  given the current Gibbs state.
		    //LOG.info("Estimating topic distribution.");
		    //double[] topicDistribution = model.getTopicProbabilities(0);

		    if (storeTopicModel) {
			if (topicModelPath == null) {
			    topicModelPath = instancesPath + ".pltm";
			}
			storeTopicModel(model, topicModelPath);
		    }

		    if (!isCalledAs("polylingual-topic-model-inference")) {
			// Show top N words in topics with proportions for the first document
			result.add(topicXMLReport(context, model, numWordsPerTopic, numTopics, languages));
			result.add(documentTopicsXMLReport(context, model, threshold, numTopics, numTopics, languages));

			if (showWordLists) {
			    // Make wordlists with topics for all instances individually.
			    // And all together even for -sample?
			    result.add(wordListsXMLReport(context, model, languages));
			}
		    }
		} catch (IOException e) {
		    throw new XPathException(this, "Error while reading instances resource: " + e.getMessage(), e);
		}

            } else {
                LOG.info("Reading stored polylingual topic model for inferencing.");
                model = readTopicModel(context, topicModelPath); 
            }
            if (isCalledAs("polylingual-topic-model-inference")) {
                LOG.info("Creating inferencers.");
		result.add(inferencedTopicsXMLReport(context, model, inferencerInstancesPath, numIterations, thinning, burnIn, languages));
	    }

            return result;

        } finally {
            context.popDocumentContext();
        }
    }

    private void cleanCaches() {
        cachedTopicModel = null;
    }

    /**
     * The method <code>readInstances</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param instancesPath a <code>String</code> value
     * @param languge a <code>String</code> value
     * @return an <code>InstanceList</code> value
     * @exception XPathException if an error occurs
     */
    public static InstanceList readInstances(XQueryContext context, final String instancesPath, final String language) throws XPathException {
        try {
	    DocumentImpl doc = (DocumentImpl) context.getBroker().getXMLResource(XmldbURI.createInternal(instancesPath + "." + language));
	    if (doc == null || doc.getResourceType() != DocumentImpl.BINARY_FILE) {
		throw new XPathException("Instances path does not point to a binary resource");
	    }
	    BinaryDocument binaryDocument = (BinaryDocument) doc;
	    File instancesFile = context.getBroker().getBinaryFile(binaryDocument).toFile();
	    if (dataDir == null) {
		dataDir = instancesFile.getParentFile();
	    }
	    return InstanceList.load(instancesFile);
        } catch (PermissionDeniedException e) {
            throw new XPathException("Permission denied to read instances resource", e);
        } catch (IOException e) {
            throw new XPathException("Error while reading instances resource: " + e.getMessage(), e);
        }
    }

    /**
     * The method <code>readInferencer</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param inferencerPath a <code>String</code> value
     * @param language a <code>String</code> value
     * @return an <code>TopicInferencer</code> value
     * @exception XPathException if an error occurs
     */
    public static TopicInferencer readInferencer(XQueryContext context, final String inferencerPath, final String language) throws XPathException {
        try {
	    DocumentImpl doc = (DocumentImpl) context.getBroker().getXMLResource(XmldbURI.createInternal(inferencerPath + "." + language));
	    if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
		throw new XPathException("Inferencer path does not point to a binary resource");
                }
                BinaryDocument binaryDocument = (BinaryDocument)doc;
                File inferencerFile = context.getBroker().getBinaryFile(binaryDocument).toFile();
                if (dataDir == null) {
                    dataDir = inferencerFile.getParentFile();
                }
                LOG.debug("Reading stored inferencer.");
                return TopicInferencer.read(inferencerFile);
        } catch (PermissionDeniedException e) {
            throw new XPathException("Permission denied to read inferencer resource", e);
        } catch (IOException e) {
            throw new XPathException("Error while reading inferencer resource: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new XPathException("Exception while reading inferencer resource", e);
        }
    }

    /**
     * The method <code>readTopicModel</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param topicModelPath a <code>String</code> value
     * @return a <code>cc.mallet.topics.PolylingualTopicModel</code> value
     * @exception XPathException if an error occurs
     */
    public static cc.mallet.topics.PolylingualTopicModel readTopicModel(XQueryContext context, final String topicModelPath) throws XPathException {
        try {
            if (topicModelSource == null || !topicModelPath.equals(topicModelSource)) {
                topicModelSource = topicModelPath;
                DocumentImpl doc = (DocumentImpl) context.getBroker().getXMLResource(XmldbURI.createInternal(topicModelPath));
                if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                    throw new XPathException("TopicModel path does not point to a binary resource");
                }
                BinaryDocument binaryDocument = (BinaryDocument)doc;
                File topicModelFile = context.getBroker().getBinaryFile(binaryDocument).toFile();
                if (dataDir == null) {
                    dataDir = topicModelFile.getParentFile();
                }
                LOG.debug("Reading stored polylingual topic model.");
                cachedTopicModel = cc.mallet.topics.PolylingualTopicModel.read(topicModelFile);
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

    private void storeTopicModel(final cc.mallet.topics.PolylingualTopicModel model, final String topicModelPath)  throws XPathException {
        XmldbURI sourcePath = XmldbURI.createInternal(topicModelPath);
        XmldbURI colURI = sourcePath.removeLastSegment();
        XmldbURI docURI = sourcePath.lastSegment();
        // References to the database
        BrokerPool brokerPool = context.getBroker().getBrokerPool();
        final org.exist.security.SecurityManager sm = brokerPool.getSecurityManager();
        Collection collection = null;
	VirtualTempFile vtf = null;

        // Start transaction
        TransactionManager txnManager = brokerPool.getTransactionManager();
	try (final DBBroker broker = brokerPool.get(Optional.ofNullable(sm.getCurrentSubject()));
	     final Txn txn = txnManager.beginTransaction()) {

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
            vtf = new VirtualTempFile(topicModelTempFile);
            try(final InputStream fis = vtf.getByteStream();
		final InputStream bis = new BufferedInputStream(fis)) {
                DocumentImpl doc = collection.addBinaryResource(txn, broker, docURI, bis, MimeType.BINARY_TYPE.getName(), vtf.length());
            }
	    vtf.close();

            // Commit change
            txnManager.commit(txn);
            
        } catch (Throwable ex) {
            throw new XPathException(this, String.format("Unable to write instances document into database: %s", ex.getMessage()));

        } finally {
	    if (vtf != null) {
                vtf.delete();
            }

            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }
        }
    }


    /**
     * The method <code>topicXMLReport</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param model a <code>cc.mallet.topics.PolylingualTopicModel</code> value
     * @param numWordsPerTopic an <code>int</code> value
     * @param numTopics an <code>int</code> value
      * @param languages a <code>List<String></code> value
     * @return a <code>NodeValue</code> value
     */
    public NodeValue topicXMLReport(final XQueryContext context, final cc.mallet.topics.PolylingualTopicModel model, final int numWordsPerTopic, final int numTopics, List<String> languages) {
	TreeSet[][] languageTopicSortedWords = new TreeSet[languages.size()][numTopics];

        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("topicModel", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
	for (int language = 0; language < languages.size(); language++) {
	    TreeSet[] topicSortedWords = languageTopicSortedWords[language];
	    int[][] typeTopicCounts = PLTMHelper.getLanguageTypeTopicCounts(model)[language];
	    for (int topic = 0; topic < numTopics; topic++) {
		topicSortedWords[topic] = new TreeSet<IDSorter>();
	    }
	    for (int type = 0; type < PLTMHelper.getVocabularySizes(model)[language]; type++) {
		
		int[] topicCounts = typeTopicCounts[type];
		
		int index = 0;
		while (index < topicCounts.length &&
		       topicCounts[index] > 0) {
		    
		    int topic = topicCounts[index] & PLTMHelper.getTopicMask(model);
		    int count = topicCounts[index] >> PLTMHelper.getTopicBits(model);
		    
		    topicSortedWords[topic].add(new IDSorter(type, count));
		    
		    index++;
		}
	    }
	}
	for (int topic = 0; topic < numTopics; topic++) {
	    builder.startElement(new QName("topic", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
	    builder.addAttribute(new QName("n", null, null), String.valueOf(topic));
	    builder.addAttribute(new QName("alpha", null, null), String.valueOf(PLTMHelper.getAlpha(model)[topic]));

	    for (int language = 0; language < languages.size(); language++) {
		builder.startElement(new QName("language", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
		builder.addAttribute(new QName("name", null, null), String.valueOf(languages.get(language)));
		builder.addAttribute(new QName("totalTokens", null, null), String.valueOf(PLTMHelper.getLanguageTokensPerTopic(model)[language][topic]));
		builder.addAttribute(new QName("beta", null, null), String.valueOf(PLTMHelper.getBetas(model)[language]));

		TreeSet<IDSorter> sortedWords = languageTopicSortedWords[language][topic];
		Alphabet alphabet = PLTMHelper.getAlphabets(model)[language];
		
		int word = 1;
		Iterator<IDSorter> iterator = sortedWords.iterator();
		while (iterator.hasNext() && word <= numWordsPerTopic) {
		    IDSorter info = iterator.next();
		    builder.startElement(new QName("token", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
		    builder.addAttribute(new QName("rank", null, null), String.valueOf(word));
		    builder.characters((CharSequence) alphabet.lookupObject(info.getID()));
		    builder.endElement();
		    word++;
		}
		builder.endElement();
	    }
	    builder.endElement();
	}
	builder.endElement();
        return (NodeValue) builder.getDocument().getDocumentElement();
    }
 
    /**
     * The method <code>wordListsXMLReport</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param model a <code>cc.mallet.topics.PolylingualTopicModel</code> value
     * @return a <code>NodeValue</code> value
     */
    public NodeValue wordListsXMLReport(final XQueryContext context, final cc.mallet.topics.PolylingualTopicModel model, final List<String> languages) {
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
	builder.startElement(new QName("wordLists", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
	for (int language = 0; language < languages.size(); language++) { 
	    builder.startElement(new QName("language", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
	    builder.addAttribute(new QName("name", null, null), String.valueOf(languages.get(language)));
	    for (int i = 0; i < model.getData().size(); i++) {
		FeatureSequence tokens = (FeatureSequence) model.getData().get(i).instances[language].getData();
		builder.startElement(new QName("wordList", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
                builder.addAttribute(new QName("n", null, null), String.valueOf(i));
		LabelSequence topics = model.getData().get(i).topicSequences[language];
		for (int position = 0; position < tokens.getLength(); position++) {
		    builder.startElement(new QName("token", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
		    builder.addAttribute(new QName("normalized-form", null, null), String.valueOf(model.getData().get(i).instances[language].getDataAlphabet().lookupObject(tokens.getIndexAtPosition(position))));
		    builder.addAttribute(new QName("topic", null, null), String.valueOf(topics.getIndexAtPosition(position)));
		    builder.endElement();
		}
		builder.endElement();
	    }
	    builder.endElement();
	}
        builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
    }

    /**
     * The method <code>documentTopicsXMLReport</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param model a <code>cc.mallet.topics.PolylingualTopicModel</code> value
     * @param threshold a <code>double</code> value
     * @param maxTopics an <code>int</code> value
     * @param numTopics an <code>int</code> value
      * @param languages a <code>List<String></code> value
     * @return a <code>NodeValue</code> value
     */
    public NodeValue documentTopicsXMLReport(final XQueryContext context, final cc.mallet.topics.PolylingualTopicModel model, final double threshold, int maxTopics, final int numTopics, List<String> languages) {
	int docLength;
	int[] topicCounts = new int[numTopics];
	IDSorter[] sortedTopics = new IDSorter[numTopics];
	for (int topic = 0; topic < numTopics; topic++) {
	    // Initialize the sorters with dummy values
	    sortedTopics[topic] = new IDSorter(topic, topic);
	}

	if (maxTopics < 0 || maxTopics > numTopics) {
	    maxTopics = numTopics;
	}

        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("documentTopics", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);

	for (int di = 0; di < model.getData().size(); di++) {
	    builder.startElement(new QName("document", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
	    builder.addAttribute(new QName("n", null, null), String.valueOf(di));

	    int totalLength = 0;

	    for (int language = 0; language < languages.size(); language++) {
		LabelSequence topicSequence = (LabelSequence) model.getData().get(di).topicSequences[language];
		int[] currentDocTopics = topicSequence.getFeatures();
		
		docLength = topicSequence.getLength();
		totalLength += docLength;
		
		// Count up the tokens
		for (int token = 0; token < docLength; token++) {
		    topicCounts[currentDocTopics[token]]++;
		}
	    }
	    for (int topic = 0; topic < numTopics; topic++) {
		sortedTopics[topic].set(topic, (float) topicCounts[topic] / totalLength);
	    }
	    Arrays.sort(sortedTopics);

	    for (int topic = 0; topic < maxTopics; topic++) {
		if (sortedTopics[topic].getWeight() < threshold) { break; }

		builder.startElement(new QName("topic", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
		builder.addAttribute(new QName("ref", null, null), String.valueOf(sortedTopics[topic].getID()));
		builder.addAttribute(new QName("weight", null, null), String.valueOf(sortedTopics[topic].getWeight()));
		builder.endElement();
	    }
	    Arrays.fill(topicCounts, 0);
	    builder.endElement();
	}
        builder.endElement();
	
        return (NodeValue) builder.getDocument().getDocumentElement();
    }


    /**
     * The method <code>inferencedTopicsXMLReport</code>
     *
     * @param context a <code>XQueryContext</code> value
     * @param model a <code>cc.mallet.topics.PolylingualTopicModel</code> value
     * @param inferencerInstancesPath a <code>String</code> value
     * @param numIterations an <code>int</code> value
     * @param thinning an <code>int</code> value
     * @param burnIn an <code>int</code> value
     * @param language an <code>String</code> value
     * @return a <code>NodeValue</code> value
     * @exception XPathException if an error occurs
     */
    public NodeValue inferencedTopicsXMLReport(final XQueryContext context, final cc.mallet.topics.PolylingualTopicModel model, final String inferencerInstancesPath, final int numIterations, final int thinning, final int burnIn, final List<String> languages) throws XPathException {
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(new QName("inferencedTopics", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);

	for (int language = 0; language < languages.size(); language++) {
	    builder.startElement(new QName("language", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
	    builder.addAttribute(new QName("name", null, null), String.valueOf(languages.get(language)));
	    LOG.info("Creating inferencer: " + languages.get(language));
	    TopicInferencer inferencer = model.getInferencer(language);
	    if (inferencer != null) {
		LOG.info("Sampling distribution.");
		InstanceList inferenceInstances = readInstances(context, inferencerInstancesPath, languages.get(language));
		for (int ii = 0; ii < inferenceInstances.size(); ii++) {
		    builder.startElement(new QName("instance", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
		    builder.addAttribute(new QName("n", null, null), String.valueOf(ii));
            
		    double[] testProbabilities = inferencer.getSampledDistribution(inferenceInstances.get(ii), numIterations, thinning, burnIn);
		    for (int tp = 0; tp < testProbabilities.length; tp++) {
			builder.startElement(new QName("topic", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX), null);
			builder.addAttribute(new QName("n", null, null), String.valueOf(tp));
			builder.addAttribute(new QName("probability", null, null), String.valueOf(numberFormatter.format(testProbabilities[tp])));
			builder.endElement();
		    }
		    builder.endElement();
		}
	    } else {
		LOG.warn("Creating inferencer: " + languages.get(language) + ", failed!");
	    }
	    builder.endElement();
	}
	builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
    }

}
