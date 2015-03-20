Official source code kept here: https://github.com/jdereg/java-util

Use the reference below to include DeepEquals and a few other powerful utilities in your project.
```
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>java-util</artifactId>
  <version>1.8.0</version>
</dependency>
```

Use `DeepEquals.deepEquals(a, b)` to compare two Java objects for semantic equality.  This will compare the objects using any custom `equals()` methods they may have (if they have an `equals()` method implemented other than `Object.equals()`).  If not, this method will then proceed to compare the objects field by field, recursively.  As each field is encountered, it will attempt to use the derived `equals()` if it exists, otherwise it will continue to recurse further.

This method will work on a cyclic Object graph like this:  A->B->C->A.  It has cycle detection so ANY two objects can be compared, and it will never enter into an endless loop.

Use `DeepEquals.hashCode(obj)` to compute a `hashCode()` for any object.  Like `deepEquals()`, it will attempt to call the `hashCode()` method if a custom `hashCode()` method (below `Object.hashCode()`) is implemented, otherwise it will compute the hashCode field by field, recursively (Deep).  Also like `deepEquals()`, this method will handle Object graphs with cycles.  For example, A->B->C->A.  In this case, `hashCode(A)` == `hashCode(B)` == `hashCode(C)`. `DeepEquals.deepHashCode()` has cycle detection and therefore will work on ANY object graph.

_When would you use it?_

If you had two 'Person' objects (person1, person2) with firstName, lastName, with the same values for these fields, yet the actual Person instance was different, and this Person object did not have a custom `equals()` or `hashCode()` method on it, then calling `person1.equals(person2)` will return false, because it will default to `Object.equals()` which relies on the storage location of the object (identity hash).  In most cases, you would rather these objects be considered equals.  In these cases, calling `DeepEquals.deepEquals(person1, person2)` will return true.  This allows you to properly handle (essentially fix) objects that do not have `equals()` and `hashCode()` methods implemented on them.

by John DeRegnaucourt