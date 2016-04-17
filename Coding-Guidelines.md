# Code Formatting
## File Names

This section lists commonly used file suffixes and names.
### File Suffixes

Java source files use the .java suffix. Compiled Java class files use the .class suffix.
### File Names

Java source files must have the same name as the public class they contain. For example, the source file containing the FooBar class must be named FooBar.java.


## File Organization

A file consists of sections that should be separated by blank lines.

Files longer than 2000 lines are cumbersome and should be avoided.
### Java Source Files

Each Java source file contains a single public class or interface. When private classes and interfaces are associated with a public class, you can put them in the same source file as the public class. The public class should be the first class or interface in the file.

Java source files have the following ordering:

1. Package and import statements
2. Class and interface declarations

 
#### Package and Import Statements

The first line of Java source files is a package statement. Following this are one or more import statements. For example:

 ```java
package com.ambry.util;
 
import org.xeril.util.Sizeable;
 
import java.util.Arrays;
import java.io.Serializable;
```
 

The first component of a package name is always written in all-lowercase ASCII letters and should be one of  the top-level domain names, currently com, edu, gov, mil, net, org, or one of the English two-letter codes identifying countries as specified in ISO Standard 3166, 1981.

 
#### Class and Interface Declarations

The following table describes the parts of a class or interface declaration, in the order that they should appear.

| Part | Description |
| --- | --- |
| Class/interface documentation Javadoc comment	| See Documentation Comments for information on what should be in this comment. |
| Class/interface statement | | 
| Class/interface implementation comment | This comment should contain any class-wide or interface-wide information that is not appropriate for the class/interface documentation comment. |
| Class variables | First the public class variables, then the protected class variables, then package scope class variables, and then private class variables. |
| Instance variables | First the public class variables, then the protected class variables, then package scope class variables, and then private class variables. |
| Constructors | |
| Methods | These methods should be grouped by functionality rather than by scope or accessibility. For example, a private class method can be in between two public instance methods. The goal is to make reading and understanding the code easier. |

 
## Indentation

Two spaces should be used as the unit of indentation. Tabs must expand to spaces and the tab width should be set to two. Tab characters must not be used in source files.
### Line Length

Lines should not exceed 120 characters.

Example code in Javadoc comments should not exceed 100 characters to accommodate page formatting.
### Wrapping Lines

When an expression will not fit on a single line, break it according to these general principles:

- Break after a comma
- Break before an operator
- Prefer higher-level breaks to lower-level breaks
- Indent the new line by four spaces relative to the beginning of the previous line

Here are some examples of breaking method calls:

```java 
someMethod(longExpression1, longExpression2, longExpression3,
    longExpression4, longExpression5);
 
var = someMethod1(longExpression1,
    someMethod2(longExpression2,
        longExpression3));
```
 

Following are two examples of breaking an arithmetic expression. The first is preferred, since the break occurs outside the parenthesized expression, which is at a higher level.

```java 
// GOOD
longName1 = longName2 * (longName3 + longName4 - longName5)
    + 4 * longname6;
 
// AVOID
longName1 = longName2 * (longName3 + longName4
    - longName5) + 4 * longname6;
```
 

Line wrapping for if statements uses four spaces. For example:

```java 
// USE THIS INDENTATION
if ((condition1 && condition2)
    || (condition3 && condition4)
    || !(condition5 && condition6)) {
  doSomethingAboutIt();
}
  
// OR USE THIS
if ((condition1 && condition2) || (condition3 && condition4)
    || !(condition5 && condition6)) {
  doSomethingAboutIt();
}
```
 

Here are three acceptable ways to format ternary expressions:

```java 
alpha = (aLongBooleanExpression) ? beta : gamma;
 
alpha = (aLongBooleanExpression) ? beta
    : gamma;
 
alpha = (aLongBooleanExpression)
    ? beta
    : gamma;
```
 
## Comments

Java programs can have two kinds of comments: implementation comments and documentation comments.  Implementation comments are those found in C++, which are delimited by /*...*/, and //.  Documentation comments (known as "doc comments" or "Javadoc comments") are Java-only, and are delimited by  /**...*/.  Doc comments can be extracted to HTML files using the javadoc tool.

Implementation comments are meant for commenting out code or for comments about the particular implementation.  Doc comments are meant to describe the specification of the code, from an implementation-free perspective to be read by developers who might not necessarily have the source code at hand.

Comments should be used to give overviews of code and provide additional information that is not readily available in the code itself. Comments should contain only information that is relevant to reading and understanding the program. For example, information about how the corresponding package is built or in what directory it resides should not be included as a comment.

Discussion of non-trivial or non-obvious design decisions is appropriate, but avoid duplicating information that is present in (and clear from) the code. It is too easy for redundant comments to get out of date. In general, avoid any comments that are likely to get out of date as the code evolves.

The frequency of comments sometimes reflects poor quality of code. When you feel compelled to add a comment, consider rewriting the code to make it clearer.

Comments should not be enclosed in large boxes drawn with asterisks or other characters.

Comments should never include special characters such as form-feed and backspace.
### Implementation Comment Formats

Programs can have two styles of implementation comments: block and single-line
#### Block Comments

Block comments are used to provide descriptions of files, methods, data structures and algorithms. Block comments may be used at the beginning of each file and before each method. They can also be used in other places, such as within methods. Block comments inside a function or method should be indented to the same level as the code they describe.

A block comment should be preceded by a blank line to set it apart from the rest of the code.

```java

/*
 * Here is a block comment.
 */
```
 
#### Single-Line Comments

Short comments can appear on a single line indented to the level of the code that follows. If a comment can't be written in a single line, it should follow the block comment format. A single-line comment should be preceded by a blank line unless it is at the start of a block. Here is an example of a single-line comment in Java code (also see Documentation Comments):

```java 
if (conditions) {
  /* Handle the first condition. */
  ...
 
  /* Handle the second condition. */
  ...
}
```
 
### Documentation Comments

This section describes the formatting of documentation comments also referred to as doc comments, or Javadoc comments. See How to Write Doc Comments for the Javadoc Tool and the Javadoc Tool home page for details on authoring documentation comments.

Doc comments describe Java classes, interfaces, constructors, methods, and fields.  Each doc comment is set inside the comment delimiters /**...*/, with one comment per class, interface, or member. This comment should appear just before the declaration:

```java 
/**
 * Provides access to the internals of the beast.
 */
public class FooBar {
  ...
}
```
 

Notice that top-level classes and interfaces are not indented, while their members are.  The first line of doc comment (/**) for classes and interfaces is not indented; subsequent doc comment lines each have 1 space of indentation (to vertically align the asterisks).  Members, including constructors, have 2 spaces for the first doc comment line and 3 spaces thereafter.

Do not repeat the name of the class, interface, method or field in the comment. It is redundant and may get missed during renaming.

If you need to give information about a class, interface, variable, or method that isn't appropriate for documentation, use an implementation block comment or single-line comment immediately after the declaration. For example, details about the implementation of a class should go in such an implementation block comment following the class statement, not in the class doc comment.

Doc comments should not be positioned inside a method or constructor definition block, because Java associates documentation comments with the first declaration after the comment.

 
### Comment types to avoid

- Please avoid TODO comments or specifying bug number in code. The code should be really clean and should not have past artifacts across the code base.

- Open a JIRA to track any future work. This helps to track things in scrum and also keeps the code clean.

- Clear code is preferable to comments. When possible make your naming so good you don't need comments. When that isn't possible comments should be thought of as mandatory, write them to be read.

- Don't be sloppy. Don't check in commented out code


## Declarations
### Number Per Line

One declaration per line is recommended since it encourages commenting.

```java
// GOOD
int level;     // Indentation level
int size;      // Number of bytes in the buffer
int[] fooarr;  // Contains the foo counts
 
// BAD
int level, size, fooarr[];
```
 
### Initialization

Try to initialize local variables where they're declared. The only reason not to initialize a variable where it's declared is if the initial value depends on some computation occurring first.

 
### Placement

Put declarations closest to where they are first used.

```java 
public void amethod() {
  ...
 
  int prime = 17;
 
  for (int i = 0; i < prime; i++) {
    ...
 
    String result = bmethod(i);
    ...
  }
 
  ...
}
```

Avoid local declarations that hide declarations at higher levels. For example, do not declare the same variable name in an inner block:

```java
public void myMethod() {
  int count = 12;
  ...
 
  if (condition) {
    int count = 0;     // AVOID!
    ...
  }
}
```
 
### Class and Interface Declarations

When coding Java classes and interfaces, the following formatting rules should be followed:

- No space between a method name and the parenthesis "(" starting its parameter list
- Open brace "{" appears on the same line as the declaration statement
- Closing brace "}" starts a line by itself indented to match the declaration
- Methods are separated by a blank line

```java 
class Sample extends Object {
  private int _ivar1;
  private int _ivar2;
 
  Sample(int i, int j) {
    _ivar1 = i;
    _ivar2 = j;
  }
     
  int emptyMethod() {
  }
}
```
 
## Statements
### Simple Statements

Each line should contain at most one statement. Example:

```java 
// GOOD
argv++;
argc--;
 
// BAD
argv++; argc--;
```
 
### Compound Statements

Compound statements are lists of statements enclosed in curly braces and should be formatted according to the following conventions:

- The enclosed statements should be indented one more level than the enclosing statement
- The opening brace should be on the same line as the enclosing statement (e.g. the 'if' clause)
- The closing brace should be on a line by itself indented to match the enclosing statement
- Braces are used around all statements, even single statements, when they are part of a control structure, such as if-else or for statements. This makes it easier to add statements without accidentally introducing bugs due to forgetting to add braces.

 
### Return Statements

A return statement with a value should not use parentheses unless they make the return value more obvious in some way. Example:

```java 
return;
 
return myDisk.size();
 
return (size == 0) ? defaultSize : size;
```
 
### if, if-else, if else-if else Statements

The if-else class of statements should have the following form:

```java 
if (condition) {
  statement;
}
 
 
if (condition) {
  statement;
} else {
  statements;
}
 
 
if (condition) {
  statement;
} else if (condition) {
  statement;
} else {
  statement;
}
```
 
### for Statements

A for statement should have the following form:

```java 
for (int i = 0; i < limit; i++) {
   ...
}
 
for (; foo != null; foo = foo.next()) {
}
 
for (String name : names) {
  ...
}
```

When using the comma operator in the initialization or update clause of a for statement, avoid the complexity of using more than three variables. If needed, use separate statements before the for loop (for the initialization clause) or at the end of the loop (for the update clause).

Do not modify the loop control variable from within the loop.
###  while Statements

A while statement should have the following form:

```java 
while (condition) {
  ...
}
 
while (condition) {
}
```
 
### do-while Statements

A do-while statement should have the following form:

```java 
do {
  ...
} while (condition);
```
 
### switch Statements

A switch statement should have the following form:

```java 
switch {
  case 12:
    ...
    break;
  case 13:
    ...
    break;
  case 14: {
    ...
    break;
  }
  case 15:
  case 16:
    ...
    break;
  default:
    ...
    break;
}
```

Avoid fall through cases (i.e. case with statements but no break) as they can easily lead to bugs when case statements are inserted at a later date.

Every switch statement should include a default case at the end of the switch block. While the break in the default case appears redundant, it prevents a fall-through error if later another case is inadvertently added after the default.

 
### try-catch Statements

A try-catch statement should have the following format:

```java 
try {
  ...
} catch (ExceptionClass e) {
  ...
}
 
try {
  ...
} catch (ExceptionClass e) {
  ...
} finally {
  ...
}
 
try {
  ...
} finally {
  ...
}
```
 
### Array Declarations

Declare arrays using the Java-style syntax, not the C-style syntax.

```java 
// GOOD
String[] names;
int[] counts;
 
// AVOID
String names[];
int counts[];
```
 
## White Space
### Blank Lines

Blank lines improve readability by setting off sections of code that are logically related.

Two blank lines should always be used in the following circumstances:

- Between sections of a source file (e.g. between the end of imports and the first class or interface definition)
- Between class and interface definitions (e.g. a class and an inner class, a public class and a private class)

One blank line should always be used in the following circumstances:

- Between the local variables in a method and its first statement
- Between methods
- Before a block or single-line comment
- Between logical sections inside a method to improve readability

### Blank Spaces

Blank spaces should be used in the following circumstances:

- A keyword followed by a parenthesis should be separated by a space. For example:
  ```java     
  while (true) {
    ...
  }
     
  if (condition) {
    ...
  }
  ```
  Note that a blank space should not be used between a method name and its opening parenthesis. This helps to distinguish keywords from method calls.
- A blank space should appear after commas in argument lists
- All binary operators except . should be separated from their operands by spaces. Blank spaces should never separate unary operators such as unary minus, increment (++), and decrement (--) from their operands. For example:
  ```java   
  a += c + d;
  a = (a + b) / (c * d);
     
  for (int i = 0; i < 20; i++) {
    n++;
  }
     
  printSize("size is " + foo + "\n");
  ```
- There should be a space surrounding the ternary operators. For example:
  ```java   
  return (x >= 0) ? x : -x;
  ```
- The expressions in a for statement should be separated by blank spaces
  Casts should be followed by a blank space. For example:
  ```java
  methodA((byte) aNum, (String) x);
  methodB((int) (cp + 5), ((int) (i + 3)) + 1);
  ```

## 1.8  Parenthesis

It is generally a good idea to use parentheses liberally in expressions involving mixed operators to avoid operator precedence problems.

```java 
// GOOD
if ((a == b) && (c == d)) {
  ...
}
 
// AVOID
if (a == b && c == d) {
  ...
}
```

If an expression containing a binary operator appears before the ? in the ternary ?: operator, it should be parenthesized.

```java
// GOOD
return (x >= 0) ? x : -x;
 
// AVOID
return x >= 0 ? x : -x;
```
 
## Naming Conventions

Naming conventions make code easier to read and understand. They provide information about the function of the identifier such as a constant, package, class or method.
### Packages	

Package names consist of all lowercase ASCII letters separated by periods.

```
com.google.common.collect
```

### Classes	

Class names should be nouns in camel case starting with an uppercase letter and using only ASCII characters. Do not prefix class names with "C". Keep class names simple and descriptive. Use whole words rather than abbreviations (e.g. use Component rather than Comp) unless the abbreviation is a common acronym (e.g. URL, HTML, DB). Apply acronyms consistently (i.e. do not mix DB and Database) using consistent letter case (e.g. do not use DB and Db). A useful rule of thumb for acronym case is to use all uppercase for two letter acronyms and mixed case for longer acronyms (e.g. DB, Html, Url).
	
```
Raster

FlowGraph

HtmlParser

DBConnection
```

### Interfaces
Interface names follow the class naming convention. Do not prefix interface names with "I".	

```
Oberserver

EventListener
```

### Methods
Methods should be verbs, in camel case starting with a lowercase letter and using only ASCII characters.	

```
run

runFast

getMajorVersion

setUrlScheme
```

###Class and instance variables	
Class and instance variable are camel case beginning with a lowercase letter, and use only ASCII characters. Use descriptive variable names.	

```
foo
```

### Local variables	
Local variables are camel case beginning with a lowercase letter and using only ASCII characters. Prefer descriptive variable names except for common temporary use cases such as loop iteration variables (e.g. i, j, k).	

```
foo

fooBar
```

### Constants	

Variables declared and static final are constants and should be all uppercase ASCII letters with words separated by an underscore ("_").
	
```
FOO

FOO_BAR
```

###Generic types	
One or more uppercase ASCII letters.	

```java
MyClass<T>

<VN> myMethod()
```