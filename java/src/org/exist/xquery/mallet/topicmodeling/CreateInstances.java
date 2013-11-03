package org.exist.xquery.mallet.topicmodeling;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.*;

import org.apache.log4j.Logger;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.types.InstanceList;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ElementImpl;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.VirtualTempFile;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.*;

import org.w3c.dom.NodeList;

/**
 * Create Instances functions to be used by most module functions of the Mallet sub-packages.
 *
 * @author ljo
 */
public class CreateInstances extends BasicFunction {
    private final static Logger LOG = Logger.getLogger(CreateInstances.class);

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
                              )
    };

    private static String instancesPath = null;
    private static File dataDir = null;
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
                parameters = ModuleUtils.parseParameters(((NodeValue)args[3].itemAt(0)).getNode());
            }
        } else if ((isCalledAs("create-instances-string") || isCalledAs("create-instances-node")) && getSignature().getArgumentCount() == 3) {
            if (!args[2].isEmpty()) {
                parameters = ModuleUtils.parseParameters(((NodeValue)args[2].itemAt(0)).getNode());
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

        try {
            if (isCalledAs("create-instances-string") || isCalledAs("create-instances-node")) {

                createInstances(createPipe(tokenRegex, useStopWords, language), getParameterValues(args[1]).toArray(new String[0]));
            } else {
                if (!args[2].isEmpty()) {
                    qname = ((QNameValue) args[2]).getQName();
                }
                createInstancesCollection(createPipe(tokenRegex, useStopWords, language), args[1].getStringValue(), qname);
            }
            if (doc == null) {
                return Sequence.EMPTY_SEQUENCE;
            } else {
                return new StringValue(instancesPath);
            }
            
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
        storeInstances(instances);
    }

    private void createInstancesCollection(Pipe pipe, String collection, final QName qname)  throws XPathException {
        DocumentSet docs = null;
        XmldbURI uri = null;
        try {
            MutableDocumentSet ndocs = new DefaultDocumentSet();
            uri = new AnyURIValue(collection).toXmldbURI();
            final Collection coll = context.getBroker().getCollection(uri);
            if (coll == null) {
                if (context.isRaiseErrorOnFailedRetrieval()) {
                    throw new XPathException("FODC0002: can not access collection '" + uri + "'");
                }
            } else {
                if (context.inProtectedMode())
                    {context.getProtectedDocs().getDocsByCollection(coll, true, ndocs);}
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
                    dlock.acquire(Lock.READ_LOCK);
                    lockAcquired = true;
                }
                DocumentImpl docImpl = new NodeProxy(doc).getDocument();
                DBBroker broker = context.getBroker();
                if (qname != null) {
                    NodeList nl = docImpl.getElementsByTagNameNS(qname.getNamespaceURI(), qname.getLocalName());

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
                    {dlock.release(Lock.READ_LOCK);}
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
        storeInstances(instances);
    }

    private void storeInstances(final InstanceList instances)  throws XPathException {
        XmldbURI sourcePath = XmldbURI.createInternal(instancesPath);
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

            File instancesTempFile = File.createTempFile("malletInstances", ".tmp");
            instancesTempFile.deleteOnExit();
            instances.save(instancesTempFile);
            VirtualTempFile vtf = new VirtualTempFile(instancesTempFile);
            InputStream bis = vtf.getByteStream();
            
            try {
                doc = collection.addBinaryResource(txn, broker, docURI, bis, MimeType.BINARY_TYPE.getName(), vtf.length());
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
