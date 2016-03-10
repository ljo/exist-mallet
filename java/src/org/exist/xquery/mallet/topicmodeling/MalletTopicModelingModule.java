package org.exist.xquery.mallet.topicmodeling;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 * Integrates the Mallet Machine Learning and Topic Modeling library.
 *
 * @author ljo
 */
public class MalletTopicModelingModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/mallet-topic-modeling";
    public final static String PREFIX = "topics";

    public final static FunctionDef[] functions = {
        new FunctionDef(CreateInstances.signatures[0], CreateInstances.class),
        new FunctionDef(CreateInstances.signatures[1], CreateInstances.class),
        new FunctionDef(CreateInstances.signatures[2], CreateInstances.class),
        new FunctionDef(CreateInstances.signatures[3], CreateInstances.class),
        new FunctionDef(CreateInstances.signatures[4], CreateInstances.class),
        new FunctionDef(CreateInstances.signatures[5], CreateInstances.class),
        new FunctionDef(CreateInstances.signatures[6], CreateInstances.class),
        new FunctionDef(CreateInstances.signatures[7], CreateInstances.class),
        new FunctionDef(TopicModel.signatures[0], TopicModel.class),
        new FunctionDef(TopicModel.signatures[1], TopicModel.class),
        new FunctionDef(TopicModel.signatures[2], TopicModel.class),
        new FunctionDef(TopicModel.signatures[3], TopicModel.class),
        new FunctionDef(TopicModel.signatures[4], TopicModel.class),
        new FunctionDef(PolylingualTopicModel.signatures[0], PolylingualTopicModel.class),
        new FunctionDef(PolylingualTopicModel.signatures[1], PolylingualTopicModel.class),
        new FunctionDef(PolylingualTopicModel.signatures[2], PolylingualTopicModel.class),
        new FunctionDef(PolylingualTopicModel.signatures[3], PolylingualTopicModel.class),
        new FunctionDef(PolylingualTopicModel.signatures[4], PolylingualTopicModel.class)
    };

    public MalletTopicModelingModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, false);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "Topic Modeling module using the Mallet Machine Learning library";
    }

    @Override
    public String getReleaseVersion() {
        return null;
    }
}
