package com.cedarsoftware.util;

import java.util.Calendar;
import java.util.Date;

import junit.framework.TestCase;

/**
 * Test for DeepEquals (equals() and hashCode())
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright [2010] John DeRegnaucourt
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class DeepEqualsTest extends TestCase
{    
    private class Person
    {
        String first;
        String last;
        Pet pet;
    }
    
    private class Pet
    {
        String name;
        String type;
    }
    
    private class ArrayClass
    {
        String name;
        Object[] items;
    }
    
    private class Cycle
    {
        String value;
        Object next;
    }      
    
    private class FixedHierarchy
    {
        String value;
        Object child1;
        Object child2;
        Object child3;
    }
    
    private class SmartPet extends Pet
    {
        // Pathological equals!!! Intentionally wrong to prove that it is called.
        public boolean equals(Object o)
        {
            if (o == null || !(o instanceof Pet))
            {
                return false;
            }
            
            Pet that = (Pet) o;
            boolean nameEquals;
            if (name == null || that.name == null)
            {
                nameEquals = name != that.name;
            }
            else
            {
                nameEquals = !name.equals(that.name);
            }
            
            if (!nameEquals)
            {
                return false;
            }
            boolean typeEquals;
            if (type == null || that.type == null)
            {
                typeEquals = type != that.type;
            }
            else
            {
                typeEquals = !type.equals(that.type);
            }
            return typeEquals;
        }
        
        public int hashCode()
        {
            int h1 = (name == null) ? 0 : name.hashCode();
            int h2 = (type == null) ? 0 : type.hashCode();
            return h1 + h2;
        }       
    }
    
    public void testHashCodeAndEquals()
    {
        Person p1 = new Person();
        p1.first = new String("John");
        p1.last = new String("DeRegnaucourt");
        int a = DeepEquals.deepHashCode(p1);
        int b = "John".hashCode();
        int c = "DeRegnaucourt".hashCode();
        assertTrue(a != b && b != c);
        
        Pet pet1 = new Pet();
        pet1.name = "Eddie";
        pet1.type = new String("dog");
        
        Pet pet2 = new Pet();
        pet2.name = "Penny";
        pet2.type = new String("dog");
        
        p1.pet = pet1;
        
        Person p2 = new Person();
        p2.first = new String("John");
        p2.last = new String("DeRegnaucourt");
        
        p2.pet = pet2;
        
        assertTrue(p1.first != p2.first);  // Ensure that the Strings are not ==
        
        assertTrue(!DeepEquals.deepEquals(p1, p2));
        assertTrue(!p1.equals(p2));
        
        p2.pet = pet1;
        assertTrue(DeepEquals.deepEquals(p1, p2));
        assertTrue(!p1.equals(p2)); // should be different because it would use Object.equals() which is instance based        
    }

    public void testCycleHandlingHashCode()
    {
        Cycle a = new Cycle();
        a.value = new String("foo");
        Cycle b = new Cycle();
        b.value = new String("bar");
        Cycle c = new Cycle();
        c.value = new String("baz");
        
        a.next = b;
        b.next = c;
        c.next = a;
        
        int ha = DeepEquals.deepHashCode(a);
        int hb = DeepEquals.deepHashCode(b);
        int hc = DeepEquals.deepHashCode(c);
        
        assertTrue(ha == hb && hb == hc);        
    }
    
    public void testCycleHandlingEquals()
    {
        Cycle a1 = new Cycle();
        a1.value = new String("foo");
        Cycle b1 = new Cycle();
        b1.value = new String("bar");
        Cycle c1 = new Cycle();
        c1.value = new String("baz");
        
        a1.next = b1;
        b1.next = c1;
        c1.next = a1;
        
        Cycle a2 = new Cycle();
        a2.value = new String("foo");
        Cycle b2 = new Cycle();
        b2.value = new String("bar");
        Cycle c2 = new Cycle();
        c2.value = new String("baz");
        
        a2.next = b2;
        b2.next = c2;
        c2.next = a2;
                
        assertTrue(DeepEquals.deepEquals(a1, a2));
        assertTrue(DeepEquals.deepEquals(b1, b2));
        assertTrue(DeepEquals.deepEquals(c1, c2));
        assertFalse(DeepEquals.deepEquals(a1, b2));
        assertFalse(DeepEquals.deepEquals(b1, c2));
        assertFalse(DeepEquals.deepEquals(c1, a2));
    }
    
    public void testHierarchyCycleEquals()
    {        
        FixedHierarchy h1 = new FixedHierarchy();
        h1.value = new String("root");
        FixedHierarchy c1 = new FixedHierarchy();
        c1.value = new String("child1");        
        FixedHierarchy c2 = new FixedHierarchy();
        c2.value = new String("child2");
        
        h1.child1 = c1;
        h1.child2 = c2;
        h1.child3 = c1;
        
        FixedHierarchy h2 = new FixedHierarchy();
        h2.value = new String("root");
        FixedHierarchy k1 = new FixedHierarchy();
        k1.value = new String("child1");        
        FixedHierarchy k2 = new FixedHierarchy();
        k2.value = new String("child2");
        
        h2.child1 = k1;
        h2.child2 = k2;
        h2.child3 = k1;
        
        assertTrue(DeepEquals.deepEquals(h1, h2));
    }
    
    public void testDeepEquals()
    {
        SmartPet smartPet1 = new SmartPet();
        smartPet1.name = new String("Fido");
        smartPet1.type = new String("Terrier");
        
        SmartPet smartPet2 = new SmartPet();
        smartPet2.name = new String("Fido");
        smartPet2.type = new String("Terrier");
        
        assertFalse(DeepEquals.deepEquals(smartPet1, smartPet2));   // Only way to get false is if it calls .equals()
        
        ArrayClass ac1 = new ArrayClass();
        ac1.name = new String("Object Array");
        ac1.items = new Object[] {new String("Hello"), 16, 16L, null, 'c', new Boolean(true), 0.04, new Object[] {"a", 2, 'c' }, new String[] {"larry", "curly", new String("mo")}};
        ArrayClass ac2 = new ArrayClass();
        ac2.name = new String("Object Array");
        ac2.items = new Object[] {new String("Hello"), 16, 16L, null, 'c', Boolean.TRUE, new Double(0.04), new Object[] {"a", 2, 'c' }, new String[] {"larry", new String("curly"), "mo"}};
        
        assertTrue(DeepEquals.deepEquals(ac1, ac2));
    }
    
    public void testBasicEquals()
    {
        String one = new String("One");
        String two = new String("Two");
        String a = new String("One");
        
        assertFalse(DeepEquals.deepEquals(one, two));
        assertTrue(DeepEquals.deepEquals(one, a));
        
        Double x = 1.04;
        Double y = 1.039999;
        Double z = 1.04;
        
        assertFalse(DeepEquals.deepEquals(x, y));
        assertTrue(DeepEquals.deepEquals(x, z));
    }
    
    public void testBasicHashCode()
    {
        String one = new String("One");
        assertTrue(DeepEquals.deepHashCode(one) == one.hashCode());
        
        Double pi = 3.14159;
        assertTrue(DeepEquals.deepHashCode(pi) == pi.hashCode());
        
        Calendar c = Calendar.getInstance();
        assertTrue(DeepEquals.deepHashCode(c) == c.hashCode());
        
        Date date = new Date();
        assertTrue(DeepEquals.deepHashCode(date) == date.hashCode());
    }
}
