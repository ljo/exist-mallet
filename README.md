exist-mallet-topic-modeling
===========================

Integrates the Mallet Machine Learning and Topic Modeling library into eXist-db.

## Compile and install

1. clone the github repository: https://github.com/ljo/exist-mallet
2. edit local.build.properties and set exist.dir to point to your eXist-db install directory
3. call "ant" in the directory to create a .xar
4. upload the xar into eXist-db using the dashboard

## Functions

There are currently three main function groups:

### topics:create-instances-*
Processes resources in the provided collection hierarchy and creates a serialized instances document which can be used by nearly all Mallet sub-packages. Returns the path to the stored instances document.
The parameter $configuration gives the configuration, eg &lt;parameters&gt;&lt;param name='stopwords' value='false'/&gt;&lt;/parameters&gt;.

topics:create-instances-collection($instances-doc as xs:anyURI, $collection-uri 
as xs:anyURI, $qname as xs:QName?) as xs:string?

topics:create-instances-collection($instances-doc as xs:anyURI, $collection-uri 
as xs:anyURI, $qname as xs:QName?, $configuration as element()) as xs:string?

topics:create-instances-node($instances-doc as xs:anyURI, $node as node()+) as 
xs:string?

topics:create-instances-node($instances-doc as xs:anyURI, $node as node()+, 
$configuration as element()) as xs:string?

topics:create-instances-string($instances-doc as xs:anyURI, $text as xs:string+) 
as xs:string?

topics:create-instances-string($instances-doc as xs:anyURI, $text as xs:string+, 
$configuration as element()) as xs:string?

### topics:topic-model-inference
Processes new instances and applies the stored topic model's inferencer. Returns the topic probabilities for the inferenced instances.

topics:topic-model-inference($instances-doc as xs:anyURI, $number-of-words-per
-topic as xs:integer, $number-of-topics as xs:integer, $number-of-iterations as 
xs:integer?, $number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w 
as xs:double?, $language as xs:string?, $instances-inference-doc as xs:anyURI) 
as node()+

topics:topic-model-inference($topic-model-doc as xs:anyURI, $instances-inference
-doc as xs:anyURI, $number-of-iterations as xs:integer, $thinning as xs:integer
?, $burn-in as xs:integer?) as node()+

### topics:topic-model
Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic.

Note: currently the language parameter does very little. It guides formating of numbers and use of stopwords though. 

topics:topic-model($instances-doc as xs:anyURI, $number-of-words-per-topic as xs
:integer, $number-of-topics as xs:integer, $number-of-iterations as xs:integer?, 
$number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w as xs:double
?, $language as xs:string?) as node()+

### topics:topic-model-sample
Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic. All other parameters use default values. Runs the model for 50 iterations and stops (this is for testing only, for real applications, use 1000 to 2000 iterations).

topics:topic-model-sample($instances-doc as xs:anyURI) as node()+

topics:topic-model-sample($instances-doc as xs:anyURI, $number-of-words-per
-topic as xs:integer, $language as xs:string?) as node()+


## Usage example

```xquery
xquery version "3.0";
import module namespace tm="http://exist-db.org/xquery/mallet-topic-modeling";
declare namespace tei="http://www.tei-c.org/ns/1.0";

let $text := 
("This is a test in English for the eXist-db@XML Prague preconference day. 
A subject as good as any. So what subjects will be chosen to label this text? ", 
"Can eXist-db really tell the subjects? Let us see now when we give two text as string arguments. ")
let $text2 := (<text>{$text[1]}</text>, <text>{$text[2]}</text>)
let $text3 := xs:anyURI("/db/dramawebben/data/works")
let $instances-doc-suffix := ".mallet"
let $topic-model-doc-suffix := ".tm"
let $instances-doc-prefix := "/db/apps/mallet-topic-modeling/resources/instances/topic-example"
let $instances-path := $instances-doc-prefix || $instances-doc-suffix
let $instances-path2 := $instances-doc-prefix || "2" || $instances-doc-suffix
let $instances-path3 := $instances-doc-prefix || "3" || $instances-doc-suffix

let $mode := 2
let $call-type := ("string", "node", "collection")[$mode]
let $instances-uri := xs:anyURI(($instances-path, $instances-path2, $instances-path3)[$mode])
let $topic-model-uri := xs:anyURI(($instances-path || $topic-model-doc-suffix, $instances-path2 || $topic-model-doc-suffix, $instances-path2 || $topic-model-doc-suffix)[$mode])

let $create-instances-p := false()
let $config := 
    <parameters>
        <param name="stopwords" value="true"/>
        <param name="language" value="sv"/>
    </parameters>
let $created := if ($create-instances-p) then 
    switch ($call-type)
        case "string" return tm:create-instances-string($instances-uri, $text, $config)
        case "node" return tm:create-instances-node($instances-uri, $text2, $config)
        case "collection" return tm:create-instances-collection($instances-uri, $text3, xs:QName("tei:body"), $config)
        default return tm:create-instances-string($instances-uri, $text)
    else ()
return 
    if ($create-instances-p) then
        tm:topic-model-inference($instances-uri, 5, 25, 50, (), (), (), "sv", $instances-uri)
        else
    tm:topic-model-inference($topic-model-uri, $instances-uri, 50, (), ())
(:  :tm:topic-model($instances-uri, 5, 25, 50, (), (), (), "sv") :)
```
