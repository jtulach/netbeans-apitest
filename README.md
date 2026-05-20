# Use the Fork!

> [!IMPORTANT]  
> The latest releases are provided by [Jakarta EE TCK Tools](https://github.com/eclipse-ee4j/jakartaee-tck-tools/tree/master/tools/sigtest). Use
> ```xml
> <dependency>
>    <groupId>jakarta.tck</groupId>
>    <artifactId>sigtest-maven-plugin</artifactId>
> </dependency>
> ```

There haven't been new releases for a while and meanwhile the active [SigTest](https://github.com/jtulach/netbeans-apitest) users set a fork up.
[According to @scottmarlow](https://github.com/eclipse-ee4j/jakartaee-tck-tools/pull/18#issuecomment-4490703395)
the latest fork of [SigTest](https://github.com/jtulach/netbeans-apitest) code is
at [jakartaee-tck-tools](https://github.com/eclipse-ee4j/jakartaee-tck-tools/tree/master/tools/sigtest).
Let's refocus around the https://github.com/eclipse-ee4j/jakartaee-tck-tools/tree/master/tools/sigtest then!

> [!NOTE]
> Another reason for switching the Maven co-ordinates is licensing.
> [NetBeans](http://netbeans.org) project had been [donated to ASF](https://netbeans.apache.org/front/main/blogs/entry/1-5-m-lines-of/)
> and changed its license to Apache. Continuing to release GPLv2 code of [SigTest](https://github.com/jtulach/netbeans-apitest) under the
> `org.netbeans.tools` namespace would be confusing. When changing the group ID, `jakarta.tck` is a decent option for
> this [little utility](https://github.com/jtulach/netbeans-apitest) of mine!

# SigTest

*SigTest* is the tool for checking incompatibilities between different versions of the same API. 
It is possible to use it as a Maven plugin or an Ant task to check for binary backward 
compatibility and mutual signature compatibility. The tool is known to work with JDK8 and JDK11 and
is used by many projects including [Graal](https://github.com/oracle/graal/commit/6ca3d0458d108ba183997f09fa51596fbe503893#diff-6229fdf88aa48f7dda4de6126283c913),
[Hibernate](https://github.com/hibernate/hibernate-validator/pull/831/files) and 
Apache [NetBeans](https://github.com/apache/incubator-netbeans/pull/670).

[![Travis Status](https://travis-ci.org/jtulach/netbeans-apitest.svg?branch=master)](https://travis-ci.org/jtulach/netbeans-apitest)

## Use in Maven

The [Maven Plugin](http://search.maven.org/#search|ga|1|a%3A%22sigtest-maven-plugin%22) is available 
on Maven central thus it is easily embeddable it into your own project. 

### Generate the Signature File

The first thing to do is to generate snapshot of API of your library - 
e.g. the signature file. Just add following into your own `pom.xml` file:

```xml
<plugin>
  <groupId>jakarta.tck</groupId> <!-- formerly <groupId>org.netbeans.tools</groupId> -->
  <artifactId>sigtest-maven-plugin</artifactId>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <release>8</release> <!-- specify version of JDK API to use 6,7,8,...15 -->
    <packages>org.yourcompany.app.api,org.yourcompany.help.api</packages>
  </configuration>
</plugin>
```

with just this change the API of your classes is going to be recorded 
into a `.sigtest` file and included as an secondary artefact of your project
when you invoke `mvn install`.

For example libraries of [Apache Html/Java API](https://github.com/apache/incubator-netbeans-html4j/) have their 
sigtest files attached in [Maven central](http://repo1.maven.org/maven2/org/netbeans/html/net.java.html.json/1.3/)
with [this changeset](https://github.com/emilianbold/netbeans-html4j/commit/3474a45f6cd1352d2366ced976a12d7d6497bc09).


### Check Against Signature File in a Repository

Once the sigfile is part of a Maven repository, you want to check your new APIs against that 
API snapshot recorded previously to make sure you are not making *incompatible changes*.
Try the following:

```xml
<plugin>
  <groupId>jakarta.tck</groupId> <!-- formerly <groupId>org.netbeans.tools</groupId> -->
  <artifactId>sigtest-maven-plugin</artifactId>
  <executions>
    <execution>
      <goals>
        <goal>check</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <packages>org.yourcompany.app.api,org.yourcompany.help.api</packages>
    <releaseVersion>1.3</releaseVersion>
    <release>8</release> <!-- specify version of JDK API to use 6,7,8,...15 -->
  </configuration>
</plugin>
```

The difference is the goal - e.g. check and also the need to specify `releaseVersion` - that is the
identification of the previously released version of your library that you want to check 
compatibility against.

And that is all! To verify the setup is correct, try to remove a method or do some other
incompatible change. When I tried and executed `mvn install` I got a build failure

```bash
SignatureTest report
Base version: 1.3
Tested version: 2.0-SNAPSHOT
Check mode: bin [throws removed]
Constant checking: on
 
 
Class net.java.html.json.Models
  "E1.2 - API type removed" : method public final static void net.java.html.json.Models.applyBindings(java.lang.Object,java.lang.String)
 
 
 
target/surefire-reports/sigtest/TEST-json-2.0-SNAPSHOT.xml: 1 failures in /.m2/repository/json/1.3/json-1.3.sigfile
------------------------------------------------------------------------
BUILD FAILURE
```

This is the way [Apache Html/Java API](https://github.com/apache/incubator-netbeans-html4j/) project
enabled signature its testing: see 
[changeset mixing both goals together](https://github.com/emilianbold/netbeans-html4j/commit/d3ef8e3208f2b04c85eafde97e4ccaf2cfe6d627).

### Fail on Error

You may want to control whether a failure in signature test should be fatal or not. Do it with:

```xml
  <configuration>
    <failOnError>false</failOnError>
 
    <packages>org.yourcompany.app.api,org.yourcompany.help.api</packages>
    <releaseVersion>1.3</releaseVersion>
  </configuration>
```  

With this configuration the test will be performed and output printed, but the build will go on.
This may be useful when one needs to do an incompatible change and wants to disable the check
until next version is published.

### Prevent Any Change

By default the plugin verifies there are no incompatible changes. However compatible changes
are allowed. Sometimes it is useful to prevent any changes altogether (when creating a bugfix release, for example), 
then try:

```xml
  <configuration>
    <action>strictcheck</action>
 
    <packages>org.yourcompany.app.api,org.yourcompany.help.api</packages>
    <releaseVersion>1.3</releaseVersion>
    <release>8</release> <!-- specify version of JDK API to use 6,7,8,...15 -->
  </configuration>
```

with the action option set to `strictcheck` the plugin will detect any API change and fail even if it is compatible. 

## Relax verification of JDK signatures

There are some cases where avoiding the verification of certain JDK classes entirely or their signatures can improve the ability to verify your API on different JDK versions.
The `-IgnoreJDKClass` option can be used to specify a set of JDK classes that can benefit from relaxed signature verification rules when it comes to dealing with JDK 
specific signature changes introduced by a later JDK version. As an example, a Signature file with @java.lang.Deprecated annotations from JDK8 may be seeing verification failures on JDK9+ 
due to `default` fields being added to @Deprecated.  With `-IgnoreJDKClass java.lang.Deprecated` enabled, verification of the @Deprecated will only check that the tested class member has the 
@Deprecated class but no verification of the @Deprecated signature will be performed. 

### Specify JDK classes to ignore in Maven plugin
To configure the JDK classes to pass to the sigtest engine with a `-IgnoreJDKClass` option, use the ignoreJDKClasses
configuration element and provide the classes to ignore using the include element as shown here:

```xml
  <configuration>
    <action>check</action>
 
    <packages>org.yourcompany.app.api,org.yourcompany.help.api</packages>
    <releaseVersion>1.3</releaseVersion>
    <ignoreJDKClasses>
      <include>java.lang.Deprecated</include>
      <include>java.lang.Object</include>
    </ignoreJDKClasses>
  </configuration>
```

## History

This tool is based on original [SigTest](https://wiki.openjdk.java.net/display/CodeTools/sigtest) sources,
but has been adopted to suite the needs of a [NetBeans project](http://wiki.netbeans.org/SignatureTest). 
Since then it evolved into [general purpose tool](http://wiki.netbeans.org/SigTest). When NetBeans become
Apache project, Emilian Bold converted the [original Hg repository](http://hg.netbeans.org/apitest/) to
[Git repository](https://github.com/emilianbold/netbeans-apitest) to preserve the history. The development,
including support for JDK11, etc. now continues at following GitHub
[repository](https://github.com/jtulach/netbeans-apitest/).

# License

You can use the *SigTest* tool to generate and verify signatures for projects released under any license. 
The sources of the tool are available in its own [github repository](https://github.com/jtulach/netbeans-apitest) 
and are provided under GPL version 2. Contribute to the development of *SigTest* by forking
the repository and creating pull requests.
