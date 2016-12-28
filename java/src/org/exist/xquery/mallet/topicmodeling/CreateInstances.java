package org.exist.xquery.mallet.topicmodeling;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.*;

import org.apache.log4j.Logger;
//import org.apache.logging.log4j.Logger;
//import org.apache.logging.log4j.LogManager;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.InstanceList;

import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.ParametersExtractor;
import org.exist.util.VirtualTempFile;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import org.w3c.dom.NodeList;

/**
 * Create Instances functions to be used by most module functions of the Mallet sub-packages.
 *
 * @author ljo
 */
public class CreateInstances extends BasicFunction {
    private final static Logger LOG = Logger.getLogger(CreateInstances.class);
    //private final static Logger LOG = LogManager.getLogger(CreateInstances.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                              new QName("create-instances-string", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes the provided text strings and creates a serialized instances document which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances document"),
                                  new FunctionParameterSequenceType("text", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "The string(s) of text to create the instances out of")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document if successfully stored, otherwise the empty sequence")
                              ),
        new FunctionSignature(
                              new QName("create-instances-string", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes the provided text strings and creates a serialized instances document which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances document"),
                                  new FunctionParameterSequenceType("text", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "The string(s) of text to create the instances out of"),
                                  new FunctionParameterSequenceType("configuration", Type.ELEMENT, Cardinality.EXACTLY_ONE,
                                                                    "The configuration, eg &lt;parameters&gt;&lt;param name='stopwords' value='false'/&gt;&lt;/parameters&gt;.")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document if successfully stored, otherwise the empty sequence")
                              ),
        new FunctionSignature(
                              new QName("create-instances-node", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes the provided nodes and creates a serialized instances document which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances document"),
                                  new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ONE_OR_MORE,
                                                                    "The node(s) to create the instances out of")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document if successfully stored, otherwise the empty sequence")
                              ),
        new FunctionSignature(
                              new QName("create-instances-node", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes the provided nodes and creates a serialized instances document which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances document"),
                                  new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ONE_OR_MORE,
                                                                    "The node(s) to create the instances out of"),
                                  new FunctionParameterSequenceType("configuration", Type.ELEMENT, Cardinality.EXACTLY_ONE,
                                                                    "The configuration, eg &lt;parameters&gt;&lt;param name='stopwords' value='false'/&gt;&lt;/parameters&gt;.")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document if successfully stored, otherwise the empty sequence")
                              ),
        new FunctionSignature(
                              new QName("create-instances-collection", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes resources in the provided collection hierarchy and creates a serialized instances document which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances document"),
                                  new FunctionParameterSequenceType("collection-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The collection hierarchy to create the instances out of"),
                                  new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.ZERO_OR_ONE,
                                                                    "The QName to restrict instance contents to, e. g. xs:QName(\"tei:body\")")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document if successfully stored, otherwise the empty sequence.")      
                              ),
        new FunctionSignature(
                              new QName("create-instances-collection", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes resources in the provided collection hierarchy and creates a serialized instances document which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances document"),
                                  new FunctionParameterSequenceType("collection-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The collection hierarchy to create the instances out of"),
                                  new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.ZERO_OR_ONE,
                                                                    "The QName to restrict instance contents to, e. g. xs:QName(\"tei:body\")"),
                                  new FunctionParameterSequenceType("configuration", Type.ELEMENT, Cardinality.EXACTLY_ONE,
                                                                    "The configuration, eg &lt;parameters&gt;&lt;param name='stopwords' value='false'/&gt;&lt;/parameters&gt;.")

                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document if successfully stored, otherwise the empty sequence.")      
                              ),
        new FunctionSignature(
                              new QName("create-instances-collection-polylingual", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes polylingual resources in the provided collection hierarchies and creates one serialized instances document per language which can be used by nearly all Mallet sub-packages. Returns the paths to the stored instances documents.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances documents. The language suffix will be added"),
                                  new FunctionParameterSequenceType("collection-uris", Type.ANY_URI, Cardinality.ONE_OR_MORE,
                                                                    "The collection hierarchies to create the instances out of. If only one is given, sub-collections for each language code are expected, otherwise collection paths for each of the languages in the same order are expected"),
                                  new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.ZERO_OR_ONE,
                                                                    "The QName to restrict instance contents to, e. g. xs:QName(\"tei:body\")"),
                                  new FunctionParameterSequenceType("languages", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "A sequence of lowercase two-letter ISO-639 codes")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The paths to the stored instances documents if successfully stored, otherwise the empty sequence.")
                              ),
        new FunctionSignature(
                              new QName("create-instances-collection-polylingual", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes polylingual resources in the provided collection hierarchies and creates a serialized instances document per language which can be used by nearly all Mallet sub-packages. Returns the paths to the stored instances documents.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances documents. The language suffix will be added"),
                                  new FunctionParameterSequenceType("collection-uris", Type.ANY_URI, Cardinality.ONE_OR_MORE,
                                                                    "The collection hierarchies to create the instances out of. If only one is given, sub-collections for each language code are expected, otherwise collection paths for each of the languages in the same order are expected"),
                                  new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.ZERO_OR_ONE,
                                                                    "The QName to restrict instance contents to, e. g. xs:QName(\"tei:body\")"),
                                  new FunctionParameterSequenceType("languages", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "A sequence of lowercase two-letter ISO-639 codes"),
                                  new FunctionParameterSequenceType("configuration", Type.ELEMENT, Cardinality.EXACTLY_ONE,
                                                                    "The configuration, eg &lt;parameters&gt;&lt;param name='stopwords' value='false'/&gt;&lt;/parameters&gt;.")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The paths to the stored instances documents if successfully stored, otherwise the empty sequence.")
                              )
    };

    private static String instancesPath = null;
    private static DocumentImpl doc = null;

    public CreateInstances(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        instancesPath = args[0].getStringValue();
        Properties parameters = new Properties();
        String stopWordsPath = null;
        Boolean useStopWords = false;
        String language = "en";
        List<String> languages = new ArrayList<String>();
        List<String> langCollections = new ArrayList<String>();
        String tokenRegex = "[\\p{L}\\p{N}_-]+";
        QName qname = null; //new QName("body", "http://www.tei-c.org/ns/1.0", "tei");
        //java.util.logging.config.level=SEVERE
        final String malletLoggingLevel = System.getProperty("java.util.logging.config.level");
        //if ("".equals(malletLoggingLevel)) {
        //    model.logger.setLevel(Level.SEVERE);
        //} else {
        //    model.logger.setLevel(malletLoggingLevel);
        //}
        context.pushDocumentContext();

        if (isCalledAs("create-instances-collection") && getSignature().getArgumentCount() == 4) {
            if (!args[3].isEmpty()) {
                parameters = ParametersExtractor.parseParameters(((NodeValue)args[3].itemAt(0)).getNode());
            }
        } else if (isCalledAs("create-instances-collection-polylingual") && getSignature().getArgumentCount() == 5) {
            if (!args[4].isEmpty()) {
                parameters = ParametersExtractor.parseParameters(((NodeValue)args[4].itemAt(0)).getNode());
	    }
        } else if ((isCalledAs("create-instances-string") || isCalledAs("create-instances-node")) && getSignature().getArgumentCount() == 3) {
            if (!args[2].isEmpty()) {
                parameters = ParametersExtractor.parseParameters(((NodeValue)args[2].itemAt(0)).getNode());
            }
        }
        
        for (String property : parameters.stringPropertyNames()) {
            if ("stopwords".equals(property)) {
                String value = parameters.getProperty(property);
                useStopWords = Boolean.valueOf(value);
            } else if ("language".equals(property)) {
                String value = parameters.getProperty(property);
                language = value;
            } else if ("file-uri".equals(property)) {
                String value = parameters.getProperty(property);
                instancesPath = value;
            }
        }

	final ValueSequence result = new ValueSequence();
        try {
            if (isCalledAs("create-instances-string") || isCalledAs("create-instances-node")) {
                createInstances(createPipe(tokenRegex, useStopWords, language), getParameterValues(args[1]).toArray(new String[0]));
            } else { // -collection-
                if (!args[2].isEmpty()) {
                    qname = ((QNameValue) args[2]).getQName();
                }

                if (languages.size() == 0 && !args[3].isEmpty()) {
		    languages = CreateInstances.getParameterValues(args[3]);
		    language = languages.get(0);
                }
		if (languages.size() == 0) {
		    createInstancesCollection(createPipe(tokenRegex, useStopWords, language), args[1].getStringValue(), qname, false, "");
		    result.add(new StringValue(instancesPath));
		} else {
		    boolean appendLangCollection = false;
		    langCollections = CreateInstances.getParameterValues(args[1]);
		    LOG.info("langCollections: " + langCollections.size() + " languages: " + languages.size());
		    if (langCollections.size() == 1 && languages.size() > 1) {
			appendLangCollection = true;
		    }
		    int i = 0;
		    for (String lang : languages) {
			doc = null;
			createInstancesCollection(createPipe(tokenRegex, useStopWords, lang), appendLangCollection ? langCollections.get(0) : langCollections.get(i), qname, appendLangCollection, lang);
			if (doc != null) {
			    result.add(new StringValue(instancesPath + "." + lang));
			}
			i++;
		    }
		}
            }
	    return result;

        } catch (IllegalArgumentException ex) {
            String errorMessage = String.format("Unable to convert to instances. %s", ex.getMessage());
            LOG.error(errorMessage, ex);
            throw new XPathException(errorMessage);
        } finally {
            context.popDocumentContext();
        }
    }

    private Pipe createPipe(final String tokenRegex, final Boolean useStopWords, final String language) {
        ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
        // Read data from File objects
        // pipeList.add(new Input2CharSequence("UTF-8"));
        // new Input2CharSequence("UTF-8").pipe(new StringReader(string))
        //    "[\\p{L}\\p{N}_]+|[\\p{P}]+"   (a group of only letters and numbers OR
        //                                    a group of only punctuation marks)
        //    "[\\p{L}\\p{N}_]+"
        Pattern tokenPattern = Pattern.compile(tokenRegex);
        pipeList.add(new CharSequence2TokenSequence(tokenPattern));
        pipeList.add(new TokenSequenceLowercase());

        // Remove stopwords from a standard English stoplist.
        //  options: [case sensitive] [mark deletions]
        if (useStopWords) {
            TokenSequenceRemoveStopwords tsrs = new TokenSequenceRemoveStopwords(false, true);
            if ("sv".equals(language)) {
                tsrs.addStopWords(stopwordsSwedish);
            } 

            pipeList.add(tsrs);
        }



        pipeList.add(new TokenSequence2FeatureSequence());

        // Do the same thing for the "target" field: 
        //  convert a class label string to a Label object,
        //  which has an index in a Label alphabet.
        pipeList.add(new Target2Label());

        // Now convert the sequence of features to a sparse vector,
        //  mapping feature IDs to counts.
        // Hmm, this does not work with the ParallelTopicModel.
        //pipeList.add(new FeatureSequence2FeatureVector());

        // Print out the features and the label
        pipeList.add(new PrintInputAndTarget());

        return new SerialPipes(pipeList);        
    }

    private void createInstances(Pipe pipe, String[] texts) throws XPathException {
        // The third argument is a Pattern that is applied to produce a class label.
        // In this case it could be the last collection name in the path.
                    
        String target = "manual-selection"; 
        ArrayIterator iterator =
            new ArrayIterator(texts, target);

        InstanceList instances = new InstanceList(pipe);

        // Process each instance provided by the iterator
        LOG.debug("Processing instances.");
        instances.addThruPipe(iterator);
        // and store it.
        LOG.debug("Storing instances.");
        storeInstances(instances, "");
    }

    private void createInstancesCollection(Pipe pipe, String collection, final QName qname, final boolean appendLangCollection, final String language)  throws XPathException {
        DocumentSet docs = null;
        XmldbURI uri = null;
        try {
            MutableDocumentSet ndocs = new DefaultDocumentSet();
            uri = new AnyURIValue(collection).toXmldbURI();
	    if (appendLangCollection) {
		uri = uri.append(language);
		LOG.info("Scanning sub-collection for language: (" + language + ") " + uri.toString());
	    } else {
		if ("".equals(language)) {
		    LOG.info("Scanning monolingual collection: " + uri.toString());
		} else {
		    LOG.info("Scanning collection for language: (" + language + ") " + uri.toString());
		}
	    }
            final Collection coll = context.getBroker().getCollection(uri);
            if (coll == null) {
                if (context.isRaiseErrorOnFailedRetrieval()) {
                    throw new XPathException("FODC0002: can not access collection '" + uri + "'");
                }
            } else {
                if (context.inProtectedMode())
                    {context.getProtectedDocs().getDocsByCollection(coll, ndocs);}
                else
                    {coll.allDocs(context.getBroker(), ndocs,
                                  true, context.getProtectedDocs());}
            }
            docs = ndocs;
        } catch (final XPathException e) { //From AnyURIValue constructor
            e.setLocation(line, column);
            throw new XPathException("FODC0002: " + e.getMessage());
        } catch(final PermissionDeniedException pde) {
            throw new XPathException("FODC0002: can not access collection '" + pde.getMessage() + "'");   
        }
        // iterate through all docs and create the node set
        final ArrayList<String> result = new ArrayList<String>(docs.getDocumentCount() + 20);
        Lock dlock;
        DocumentImpl doc;
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext();) {
            doc = i.next();
            dlock = doc.getUpdateLock();
            boolean lockAcquired = false;
            try {
                if (!context.inProtectedMode() && !dlock.hasLock()) {
                    dlock.acquire(LockMode.READ_LOCK);
                    lockAcquired = true;
                }
                DocumentImpl docImpl = new NodeProxy(doc).getOwnerDocument();
                DBBroker broker = context.getBroker();
                if (qname != null) {
                    NodeList nl = docImpl.getElementsByTagNameNS(qname.getNamespaceURI(), qname.getLocalPart());

                    for (int ei =0; ei < nl.getLength(); ei++) {
                        result.add(new String(broker.getNodeValue((ElementImpl) nl.item(ei), true).replaceAll("­\\s*", "")));
                    }
                    
                } else {
                    result.add(new String(broker.getNodeValue((ElementImpl) docImpl.getDocumentElement(), true)).replaceAll("­\\s*", ""));
                }


            } catch (final LockException e) {
                throw new XPathException(e.getMessage());
            } finally {
                if (lockAcquired)
                    {dlock.release(LockMode.READ_LOCK);}
            }
        }
        
        // The third argument is a Pattern that is applied to produce a class label.
        // In this case it could be the last collection name in the path.
        String target = uri.toString();
        ArrayIterator iterator =
            new ArrayIterator(result, target);

        InstanceList instances = new InstanceList(pipe);

        // Process each instance provided by the iterator
        instances.addThruPipe(iterator);
        // and store it.
	if ("".equals(language)) {
	    LOG.info("Storing instances for monolingual collection");
	} else {
	    LOG.info("Storing instances for polylingual language collection: " + language);

	}
        storeInstances(instances, language);
    }

    private void storeInstances(final InstanceList instances, final String language)  throws XPathException {
	final String langInstancesPath = "".equals(language) ? instancesPath : instancesPath  + "." + language;
        XmldbURI sourcePath = XmldbURI.createInternal(langInstancesPath);
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

            collection = broker.openCollection(colURI, LockMode.WRITE_LOCK);
            if (collection == null) {
                String errorMessage = String.format("Collection %s does not exist", colURI);
                LOG.error(errorMessage);
                txnManager.abort(txn);
                throw new XPathException(this, errorMessage);
            }


            // Stream into database

            File instancesTempFile = File.createTempFile("malletInstances", ".tmp");
            instancesTempFile.deleteOnExit();
            instances.save(instancesTempFile);
            vtf = new VirtualTempFile(instancesTempFile);
            try(final InputStream fis = vtf.getByteStream();
		final InputStream bis = new BufferedInputStream(fis)) {
                doc = collection.addBinaryResource(txn, broker, docURI, bis, MimeType.BINARY_TYPE.getName(), vtf.length());
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
                collection.release(LockMode.WRITE_LOCK);
            }
        }
    }

    /**
     * The method <code>getParameterValues</code>
     *
     * @param parameter a <code>Sequence</code> value
     * @return a <code>List<String></code> value
     * @exception XPathException if an error occurs
     */
    public static List<String> getParameterValues(Sequence parameter) throws XPathException {
        final List<String> args = new ArrayList<String>();
        for (final SequenceIterator j = parameter.iterate(); j.hasNext();) {
            final Item next = j.nextItem();
            args.add(next.getStringValue());
        }
        return args;
    }

    static final String[] stopwordsSwedish = {
        // Actual words from test texts (negations and numbers)
        "ej", "icke", "inte", "utan", "en", "een", "ett", "noll", "två", "tre",
        "1", "2", "3", "4", "5", "6", "7", "9", "10", "11", "12", "13", "14", "15",
        "16", "17", "18", "19", "20", "21", "25", "30", "40", "50", "60", "70", "75",
        "80", "85", "90", "100",
        // Actual words from test texts (verbs)
        "ha", "har", "hade", "hafva", "hafwa", "var", "war", "är", "äro", "vara", "varit", "voro", "woro", "sa", "säger", "sade", "se", "ser", "såg", "ska", "skall", "skulle", "gå", "gick", "få", "får", "fick", "kan", "kunna", "kunde", "bli", "blifva", "blir", "blev", "blef", "blivit", "blifvit", "gör", "göra", "gjorde", "gjort", "vill", "ville", "kom", "komma", "kommer",
        // Actual words from test texts (English, German, French et al)
        "the", "to",
        "und", "die", "der", "das", "ich", "er", "es", "ist", "des", "zu", "nicht", "ein", 
        "von", "wie", "als", "nur", "aber", "auch", 
        "est", "je", "les", "il", "une", "vous",
        // Actual words from test texts (truncations and accronyms)
        "sg", "ss", "ff", "ii", "iii", "iv", "jfr", "dä", "ä", "egh", "dgr", "ep", "lp", "anm", "hr", "kl", "mr",
        // Actual words from test texts
        "och", "ock", "oc", "också", "eller", "att", "som", "så", "såsom", "men", "om", "då", "här", "där", "ty", "nu", "ju", "jo", "därför", "derför", "derföre", "derefter", "deraf", "derpå",
        "således", "både", "hur", "mer", "mycket", "mera", "endast", "bara", "åter", "ja", "nej", "aldrig", "alla", "allt", "alt", "hel", "hela", "helt", "alldeles", "ännu", "redan", "dock", "ty", "ehuru", "därefter", "emellertid", "hit", "dit", "väl", "wäl", "måste", "nog",
        "liksom", "varför", "hvarför", "övrigt", "öfrigt", "herr", "herrn", "fru", "frun", "ack", "medan", "sådant", "äller", "hellre", "snarare", "eljest", "annars",
        // Actual words from test texts
        "av", "af", "aff", "i", "efter", "fram", "från", "för", "med", "medh", "ner", "ned", "på", "ut", "uti", "til", "till", "upp", "vid", "wid", "å", "över", "öfver", "öfwer", 
        // List of additional prepositions
        "à", "á", "alltefter", "alltifrån", "alltintill", "alltsedan", "angående", "apropå", "av", "bak", "bakefter", "baki", "bakifrån", "bakom", "bakpå", "bakvid", "baköver", "beträffande", "bland", "bortanför", "bortefter", "bortemot", "bortifrån", "bortigenom", "bortom", "bortåt", "bortöver", "bredvid", "brevid", "efter", "emellan", "emot", "enligt", "exklusive", "framemot", "framför", "framom", "frampå", "framåt", "från", "frånsett", "för", "förbi", "före", "förutan", "förutom", "förutsatt", "genom", "gentemot", "givet", "gällande", "hinsides", "hitom", "hos", "hänemot", "härom", "i", "ibland", "ifrån", "igenom", "ikring", "inemot", "inför", "ini", "inifrån", "inigenom", "inklusive", "innan", "innanför", "inom", "inpå", "intill", "inunder", "inuti", "invid", "inåt", "inöver", "jämlikt", "jämsmed", "jämte", "kontra", "kring", "längs", "längsefter", "längsmed", "med", "medels", "medelst", "medio", "mellan", "minus", "mittemellan", "mittemot", "mittför", "mittibland", "mot", "nedan", "nedanför", "nedemot", "nedför", "nedifrån", "nedom", "nedströms", "nedåt", "neremot", "nerför", "nerifrån", "nerom", "nerströms", "neråt", "oaktat", "oansett", "oavsett", "om", "omkring", "oppefter", "oppför", "oppifrån", "oppströms", "oppå", "oppåt", "ovan", "ovanför", "ovanom", "ovanpå", "per", "plus", "pluss", "på", "relativt", "runt", "runtikring", "runtom", "runtomkring", "rörande", "sedan", "sen", "till", "tills", "trots", "tvärsemot", "tvärsigenom", "tvärsöver", "tvärtemot", "undan", "undantagandes", "under", "uppefter", "uppför", "uppifrån", "uppom", "uppströms", "uppå", "uppåt", "ur", "utan", "utanför", "utanpå", "utav", "utefter", "utför", "uti", "utifrån", "utmed", "utom", "utur", "utåt", "utöver", "via", "vid", "visavi", "västerifrån", "å", "åt", "över",

        // Actual words from test texts
        "hon", "henne", "hennes", "han", "hans", "honom", "jag", "iag", "iagh", "mig", "migh", "min", "mitt", "mina", "du", "dig", "dej", "din", "dina", "vem", "sig", "sin", "sitt", "sina", "den", "det", "thet", "ther", "denna", "denne", "detta", "man", "de", "dem", "vi", "oss", "vår", "våra", "vårt", "själv", "sjelf", "själf", "själva", "sjelfva", "ni", "er", "ers", "dess", "dessa", "deras", "andra", "än", 
        "vad", "hvad", "hwad", "hvilken", "hwilken", "vilket", "hvilket", "vilka", "hvilka", "huru", "när", "även", "äfven", "äfwen", "begge", "andra", "några", "hvars", "hvarje",

        // List of additional forms of one old spelling pronoun
        "hva", "hvar", "ha", "har", "hvas", "hvæs", "hves", "hvess", "hvem", "hvan", "hvem", "hven", "hvat", "hvadh", "hvas", "hvæs", "hvi", "hvat", "hvadh",

        // List of additional pronouns
        "all", "allesammans", "allihop", "allihopa",
        "alltfler", "alltflera", "alltihop", "alltihopa",
        "allting", "alltsammans", "alltsamman",
        "ann", "annan", "annat", "andra", "blott", "båda", "bägge", "bådadera", "varandra",
        "bäggedera", "de", "denna", "densamma", "du", "dylik",
        "eho", "ehurudan", "endera", "ettdera", "envar", "evem",
        "flera", "han", "hans", "honom", "hon", "henne", "hennes", "hurdan", "hurudan", 
        "ingen", "inget", "inga", "ingendera", "ingenting", "intet", "intetdera",
        "jag", "jättemånga", "mången", "mycken",
        "ni", "evad", "folk", "hen", "lite", "liten", "litet",
        "någon", "någondera", "någonting", "något", "samma", "samt", "samtliga",    
        "sin", "sitt", "sina", "slik", "somlig", "somt", "sådan",
        "tji",
        "vad", "vadhelst", "varandra", "varannan", "vardera", "varenda", "varje", "vars", 
        "varsin", "själv", "så", "vem", "vi", "vilken", "vilkendera",
        "åtskillig", "ömse", "övrig",

    };
}
