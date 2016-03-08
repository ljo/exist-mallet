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

topics:create-instances-node($instances-doc as xs:anyURI, $node as node()+) as xs:string?

topics:create-instances-node($instances-doc as xs:anyURI, $node as node()+, $configuration as element()) as xs:string?

topics:create-instances-string($instances-doc as xs:anyURI, $text as xs:string+) as xs:string?

topics:create-instances-string($instances-doc as xs:anyURI, $text as xs:string+, $configuration as element()) as xs:string?

### topics:topic-model-inference / topics:polylingual-topic-model-inference
Processes new instances and applies the stored topic model's inferencer(s). Returns the topic probabilities for the inferenced instances.

topics:topic-model-inference($instances-doc as xs:anyURI, $number-of-words-per-topic as xs:integer, $number-of-topics as xs:integer, $number-of-iterations as xs:integer?, $number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w as xs:double?, $language as xs:string?, $instances-inference-doc as xs:anyURI) as node()+

topics:topic-model-inference($topic-model-doc as xs:anyURI, $instances-inference-doc as xs:anyURI, $number-of-iterations as xs:integer, $thinning as xs:integer?, $burn-in as xs:integer?) as node()+

topics:polylingual-topic-model-inference($instances-doc as xs:anyURI, $number-of-words-per
-topic as xs:integer, $number-of-topics as xs:integer, $number-of-iterations as xs:integer?, $number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w as xs:double?, $languages as xs:string+, $instances-inference-doc as xs:anyURI) as node()+

topics:polylingual-topic-model-inference($topic-model-doc as xs:anyURI, $instances-inference
-doc as xs:anyURI, $number-of-iterations as xs:integer, $thinning as xs:integer?, $burn-in as xs:integer?, $languages as xs:string+) as node()+

### topics:topic-model / topics:polylingual-topic-model
Processes instances and creates either a monolingual topic model or a polylingual topic model (PLTM) which can be used for inference. Returns the specified number of top ranked words per topic.

Note: currently the language parameter does very little. It guides formating of numbers and use of stopwords (not for polylingual) though. For PLTM it is very important to tune the parameters otherwise it might even kill the whole jvm. 

topics:topic-model($instances-doc as xs:anyURI, $number-of-words-per-topic as xs:integer, $number-of-topics as xs:integer, $number-of-iterations as xs:integer?, $number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w as xs:double?, $language as xs:string?) as node()+

topics:polylingual-topic-model($instances-doc as xs:anyURI, $number-of-words-per-topic as xs
:integer, $number-of-topics as xs:integer, $number-of-iterations as xs:integer?, $number-of-threads as xs:integer?, $alpha_t as xs:double?, $beta_w as xs:double?, $languages as xs:string+) as node()+

### topics:topic-model-sample
Processes instances and creates a topic model which can be used for inference. Returns the specified number of top ranked words per topic. All other parameters use default values. Runs the model for 50 iterations and stops (this is for testing only, for real applications, use 1000 to 2000 iterations).

topics:topic-model-sample($instances-doc as xs:anyURI) as node()+

topics:topic-model-sample($instances-doc as xs:anyURI, $number-of-words-per-topic as xs:integer, $language as xs:string?) as node()+


## Usage example, monolingual

```xquery
xquery version "3.0";
import module namespace tm="http://exist-db.org/xquery/mallet-topic-modeling";
declare namespace tei="http://www.tei-c.org/ns/1.0";

let $text := 
("This is a test in English for the eXist-db@XML Prague preconference day. 
A subject as good as any. So what subjects will be chosen to label this text? ", 
"Can eXist-db really tell the subjects? Let us see now when we give two strings as arguments. ")
let $text2 := (<text>{$text[1]}</text>, <text>{$text[2]}</text>)
let $text3 := xs:anyURI("/db/dramawebben/data/works")
let $instances-doc-suffix := ".mallet"
let $topic-model-doc-suffix := ".tm"
(: Make sure the collection path you use here exists. :)
let $instances-doc-prefix := "/db/temp/topic-example"
let $instances-path := $instances-doc-prefix || $instances-doc-suffix
let $instances-path2 := $instances-doc-prefix || "2" || $instances-doc-suffix
let $instances-path3 := $instances-doc-prefix || "3" || $instances-doc-suffix

let $mode := 1
let $call-type := ("string", "node", "collection")[$mode]
let $instances-uri := xs:anyURI(($instances-path, $instances-path2, $instances-path3)[$mode])
let $topic-model-uri := xs:anyURI(($instances-path || $topic-model-doc-suffix, $instances-path2 || $topic-model-doc-suffix, $instances-path2 || $topic-model-doc-suffix)[$mode])

(: Make sure you create an instance for the mode you use. :)
let $create-instances-p := true()
(: Please note this is an example configuration. :)
let $config := 
    <parameters>
        <param name="stopwords" value="true"/>
        <param name="language" value="sv"/>
        <param name="useStored" value="true"/>
        <param name="showWordLists" value="true"/>
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
        tm:topic-model($instances-uri, 5, 15, 50, (), (), (), "sv")
	(: tm:topic-model-inference($instances-uri, 5, 15, 50, (), (), (), "sv", $instances-uri) :)
    else
        tm:topic-model-inference($topic-model-uri, $instances-uri, 50, (), ())
```

## Usage example, polylingual topic modeling (PLTM)

```xquery
xquery version "3.0";
import module namespace tm="http://exist-db.org/xquery/mallet-topic-modeling";
declare namespace tei="http://www.tei-c.org/ns/1.0";
let $languages := ("sv", "en")
let $text-sv := 
("Det här är en text för förkonferensdagen@XML-Prag. 
Ett ämne gott som något. Så vilket ämne kommer att tilldelas som etikett för den här texten? ", 
"Kan eXist-db verkligen skilja på ämnena? Vad händer om vi ger två strängar som argument? ")
let $text-en := ("This is a test for the eXist-db@XML Prague preconference day. 
A subject as good as any. So what subjects will be chosen to label this text? ", 
"Can eXist-db really tell the subjects? Let us see now when we give two strings as arguments. ")
let $text2 := (<text><text>{$text-sv[1]}</text><text>{$text-sv[2]}</text></text>, 
               <text><text>{$text-en[1]}</text><text>{$text-en[2]}</text></text>)
let $text3 := xs:anyURI("/db/dramawebben/data/works")
let $instances-doc-suffix := ".mallet"
let $topic-model-doc-suffix := ".pltm"
(: Make sure the collection path you use here exists. :)
 
let $instances-doc-prefix := "/db/temp/topic-example"
let $instances-path := $instances-doc-prefix || $instances-doc-suffix
let $instances-path2 := $instances-doc-prefix || "2" || $instances-doc-suffix
let $instances-path3 := $instances-doc-prefix || "3" || $instances-doc-suffix

(: Mode 3 is still under development for PLTM :)
let $mode := 2
let $call-type := ("string", "node", "collection")[$mode]
let $instances-uri := xs:anyURI(($instances-path, $instances-path2, $instances-path3)[$mode])
let $instances-uris := for $lang in $languages return xs:anyURI(concat(($instances-path, $instances-path2, $instances-path3)[$mode], ".", $lang))
let $topic-model-uri := xs:anyURI(($instances-path || $topic-model-doc-suffix, $instances-path2 || $topic-model-doc-suffix, $instances-path2 || $topic-model-doc-suffix)[$mode])

(: Make sure you create an instance for the mode you use at least once. :)
let $create-instances-p := true()
(: Please note this is an example configuration. :)
let $config := 
    <parameters>
        <param name="stopwords" value="true"/>
        <param name="languages" value="sv"/>
        <param name="languages" value="en"/>
        <param name="useStored" value="false"/>
        <param name="showWordLists" value="true"/>
    </parameters>
let $created := if ($create-instances-p) then 
    switch ($call-type)
        case "string" return for $instance-uri at $pos in $instances-uris return tm:create-instances-string($instance-uri, ($text-sv, $text-en)[$pos], $config)
        case "node" return for $instance-uri at $pos in $instances-uris return tm:create-instances-node($instance-uri, $text2[$pos]/text, $config)
        case "collection" return for $instance-uri at $pos in $instances-uris return tm:create-instances-collection($instance-uri, $text3[$pos], xs:QName("tei:body"), $config)
        default return for $instance-uri at $pos in $instances-uri return tm:create-instances-string($instance-uri, ($text-sv, $text-en)[$pos], $config)
    else ()
return 
    if ($create-instances-p) then
        tm:polylingual-topic-model($instances-uri, 5, 5, 50, (), (), (), $languages)
        (:tm:polylingual-topic-model-inference($instances-uri, 10, 25, 750, (), (), (), $languages, $instances-uri) :)
    else
        tm:polylingual-topic-model-inference($topic-model-uri, $instances-uri, 750, (), (), $languages)
    (: tm:polylingual-topic-model($instances-uri, 5, 25, 50, (), (), (), ("sv")) :)
```
