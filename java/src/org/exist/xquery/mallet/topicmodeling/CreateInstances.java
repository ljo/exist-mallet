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
                              "Processes the provided text strings and creates serialized instances which can be used by neraly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances."),
                                  new FunctionParameterSequenceType("text", Type.STRING, Cardinality.ONE_OR_MORE,
                                                                    "The string(s) of text to create the instances out of.")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document  if successfully stored, otherwise the empty sequence.")
                              ),
        new FunctionSignature(
                              new QName("create-instances-node", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes the provided nodes and creates serialized instances which can be used by neraly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances."),
                                  new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ONE_OR_MORE,
                                                                    "The node(s) to create the instances out of.")
                              },
                              new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                                                             "The path to the stored instances document if successfully stored, otherwise the empty sequence.")
                              ),
        new FunctionSignature(
                              new QName("create-instances-collection", MalletTopicModelingModule.NAMESPACE_URI, MalletTopicModelingModule.PREFIX),
                              "Processes resources in the provided collection hierachy and creates serialized instances which can be used by neraly all Mallet sub-packages. Returns the path to the stored instances document.",
                              new SequenceType[] {
                                  new FunctionParameterSequenceType("instances-doc", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                                                                    "The path within the database to where to store the serialized instances."),
                                  new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                                                                    "The collection hierachy to create the instances out of.")
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
        // args[1] = source
        String stopWordsPath = null; 
        String tokenRegex = "[\\p{L}\\p{N}_]+";
        //stopWordsPath args[2].getStringValue();
        //tokenRegex args[3].getStringValue();

        context.pushDocumentContext();

        try {
            if (isCalledAs("create-instances-string")) {
                String text = args[1].getStringValue();
                createInstances(createPipe(tokenRegex), new String[] {text});
                if (doc == null) {
                    return Sequence.EMPTY_SEQUENCE;
                } else {
                    return new StringValue(instancesPath);
                }
            } else {
                NodeValue nodeValue = (NodeValue) args[1].itemAt(0);
                createInstances(createPipe(tokenRegex), new NodeValue[] {nodeValue});
                if (doc == null) {
                    return Sequence.EMPTY_SEQUENCE;
                } else {
                return new StringValue(instancesPath);
                }
            }
        } catch (IllegalArgumentException ex) {
            String errorMessage = String.format("Unable to convert to instances. %s", ex.getMessage());
            LOG.error(errorMessage, ex);
            throw new XPathException(errorMessage);
        } finally {
            context.popDocumentContext();
        }
    }

    private Pipe createPipe(final String tokenRegex) {
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
        // pipeList.add(new TokenSequenceRemoveStopwords(false, false));

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
        
        String target = "collection-name"; 
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

    private void createInstances(Pipe pipe, NodeValue[] nodeValues)  throws XPathException {
        // The third argument is a Pattern that is applied to produce a class label.
        // In this case it could be the last collection name in the path.
        String target = "collection-name"; 
        ArrayIterator iterator =
            new ArrayIterator(nodeValues, target);

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
}
