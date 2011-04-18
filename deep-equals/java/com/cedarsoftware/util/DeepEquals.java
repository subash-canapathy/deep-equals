package com.software.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deeply compare two (2) objects.  This method will call any overridden
 * equals() methods if they exist.  If not, it will then proceed to do a
 * field-by-field comparison, and when a non-primitive field is encountered,
 * recursively continue the deep comparison. When an array is found, it will
 * also ensure that the array contents are deeply equal, not requiring the 
 * array instance (container) to be identical.  This method will successfully 
 * compare object graphs that have cycles (A->B->C->A).  There is no need to
 * ever use the Arrays.deepEquals() method as this is a true and more effective
 * super set.
 *  
 * @author John DeRegnaucourt
 */
public class DeepEquals
{
    private static final Map<Class, Boolean> _customEquals = new ConcurrentHashMap<Class, Boolean>();
    private static final Map<Class, Boolean> _customHash = new ConcurrentHashMap<Class, Boolean>();
    private static final Map<Class, Collection<Field>> _reflectedFields = new ConcurrentHashMap<Class, Collection<Field>>();
    
    private static class DualKey
    {
        private Object _key1;
        private Object _key2;
        
        private DualKey() { }
        private DualKey(Object k1, Object k2)
        {
            _key1 = k1;
            _key2 = k2;
        }
        
        public boolean equals(Object other)
        {
            if (other == null)
            {
                return false;
            }
            
            if (!(other instanceof DualKey))
            {
                return false;
            }
            
            DualKey that = (DualKey) other;
            return _key1 == that._key1 && _key2 == that._key2;
        }
        
        public int hashCode()
        {
            int h1 = _key1 != null ? _key1.hashCode() : 0;
            int h2 = _key2 != null ? _key2.hashCode() : 0;
            return h1 + h2;
        }
    }
    
    public static boolean deepEquals(Object a, Object b)
    {
        Set visited = new HashSet<DualKey>();
        return deepEquals(a, b, visited);
    }
    
    public static boolean deepEquals(Object a, Object b, Set visited)
    {
        LinkedList<DualKey> stack = new LinkedList<DualKey>();
        stack.addFirst(new DualKey(a, b));

        while (!stack.isEmpty())
        {
            DualKey dualKey = stack.removeFirst();        
            visited.add(dualKey);
            
            if (dualKey._key1 == null || dualKey._key2 == null)
            {
                if (dualKey._key1 != dualKey._key2)
                {
                    return false;
                }
                continue;
            }
                            
            if (!dualKey._key1.getClass().equals(dualKey._key2.getClass()))
            {
                return false;
            }
            
            if (dualKey._key1.getClass().isArray())
            {
                int len = Array.getLength(dualKey._key1);
                if (len != Array.getLength(dualKey._key2))
                {
                    return false;
                }
                    
                for (int i = 0; i < len; i++)
                {
                    DualKey dk = new DualKey(Array.get(dualKey._key1, i), Array.get(dualKey._key2, i));
                    if (!visited.contains(dk))
                    {
                        stack.addFirst(dk);
                    }
                }
                continue;
            }
            
            // Set comparison is complex.  First, the easy check - make sure the sets are the same length.
            // Next, verify that the two sets have the same deep Hash code (this is computed in linear time).
            // If both sets have same length and same hash, then ensure all elements from one set are deepEquals
            // to all elements in the other Set (O(N^2).
            if (dualKey._key1 instanceof Set)
            {
                if (!compareUnordered((Set)dualKey._key1, (Set) dualKey._key2, visited))
                {
                    return false;
                }
                continue;
            }
            
            // Check any Collection that is not a Set.  In these cases, element order
            // matters, therefore this comparison is faster than using unordered comparison.
            if (dualKey._key1 instanceof Collection)
            {
                Collection col1 = (Collection) dualKey._key1;
                Collection col2 = (Collection) dualKey._key2;
                if (col1.size() != col2.size())
                {
                    return false;
                }
                                
                Iterator i1 = col1.iterator();
                Iterator i2 = col2.iterator();
                
                while (i1.hasNext())
                {
                    DualKey dk = new DualKey(i1.next(), i2.next());
                    if (!visited.contains(dk))
                    {
                        stack.addFirst(dk);
                    }
                }
                                
                continue;
            }
            
            if (dualKey._key1 instanceof Map)
            {
                Map<Object, Object> map1 = (Map) dualKey._key1;
                Map<Object, Object> map2 = (Map) dualKey._key2;
                
                if (map1.size() != map2.size())
                {
                    return false;
                }
                                
                for (Map.Entry entry1 : map1.entrySet())
                {
                    Map.Entry saveEntry2 = null;
                    for (Map.Entry entry2 : map2.entrySet())
                    {   // recurse here (yes, that makes this a Stack-based implementation with partial recursion in
                        // the case of Map keys).
                        if (deepEquals(entry1.getKey(), entry2.getKey(), visited))
                        {
                            saveEntry2 = entry2;
                            break;
                        }                        
                    }
                    
                    if (saveEntry2 == null)
                    {
                        return false;
                    }
                    
                    DualKey dk = new DualKey(entry1.getValue(), saveEntry2.getValue());
                    if (!visited.contains(dk))
                    {
                        stack.addFirst(dk);
                    }
                }
                                                
                continue;
            }            
            
            if (hasCustomEquals(dualKey._key1.getClass()))
            {
                if (!dualKey._key1.equals(dualKey._key2))
                {
                    return false;
                }
                continue;
            }        
            
            Collection<Field> fields = getDeepDeclaredFields(dualKey._key1.getClass());               
            
            for (Field field : fields)
            {
                try
                {
                    DualKey dk = new DualKey(field.get(dualKey._key1), field.get(dualKey._key2));
                    if (!visited.contains(dk))
                    {
                        stack.addFirst(dk);
                    }
                }
                catch (Exception e)
                {
                    continue;
                }
            }
        }

        return true;
    }
    
    /**
     * Deeply compare the two sets referenced by dualKey.  This method
     * attempts to quickly determine inequality by length, then hash,
     * and finally does a deepEquals on each element if the two Sets
     * passed by the prior tests.
     * @param col1 Collection one
     * @param col2 Collection two
     * @param visited Set containing items that have already been compared,
     * so as to prevent cycles.
     * @return boolean true if the Sets are deeply equals, false otherwise.
     */
    private static boolean compareUnordered(Collection col1, Collection col2, Set visited)
    {
        if (col1.size() != col2.size())
        {
            return false;
        }
        
        int h1 = deepHashCode(col1);
        int h2 = deepHashCode(col2);
        if (h1 != h2)
        {   // Faster than deep equals compare (O(n^2) comparison can be skipped when not equal)
            return false;
        }
        
        for (Object element1 : col1)
        {
            boolean found = false;
            for (Object element2 : col2)
            {   // recurse here (yes, that makes this a Stack-based implementation with partial recursion in
                // the case of Sets).
                if (deepEquals(element1, element2, visited))
                {
                    found = true;
                    break;
                }                        
            }
            
            if (!found)
            {
                return false;
            }
        }
                        
        return true;        
    }      
        
	/**
	 * Test whether the passed in Class has an equals() 
	 * method that is custom (not using Object.equals()).
	 */
    public static boolean hasCustomEquals(Class c)
    {        
        Class origClass = c;
        if (_customEquals.containsKey(c))
        {
            return _customEquals.get(c);
        }

        while (!Object.class.equals(c))
        {
            try
            {
                c.getDeclaredMethod("equals", Object.class);
                _customEquals.put(origClass, true);
                return true;
            }
            catch (Exception ignored) { }
            c = c.getSuperclass();
        }
        _customEquals.put(origClass, false);
        return false;
    }

	/**
	 * Test whether the passed in Class has a hashCode() 
	 * method that is custom (not using Object.hashCode()).
	 */
    public static int deepHashCode(Object obj)
    {
        Set visited = new HashSet();
        LinkedList<Object> stack = new LinkedList<Object>();
        stack.addFirst(obj);
        int hash = 0;

        while (!stack.isEmpty())
        {
            obj = stack.removeFirst();
            if (obj == null || visited.contains(obj))
            {
                continue;
            }
            
            visited.add(obj);
            
            if (obj.getClass().isArray())
            {
                int len = Array.getLength(obj);
                for (int i = 0; i < len; i++)
                {        
                    stack.addFirst(Array.get(obj, i));
                }
                continue;
            }
                                    
            if (obj instanceof Collection)
            {      
                stack.addAll(0, (Collection)obj);
                continue;
            }
            
            if (obj instanceof Map)
            {
                stack.addAll(0, ((Map)obj).keySet());
                stack.addAll(0, ((Map)obj).values());
                continue;                
            }
            
            if (hasCustomHashCode(obj.getClass()))
            {   // A real hashCode() method exists, call it.
                hash += obj.hashCode();
                continue;
            }
                        
            Collection<Field> fields = getDeepDeclaredFields(obj.getClass());
            for (Field field : fields)
            {
                try
                {           
                    stack.addFirst(field.get(obj));
                }
                catch (Exception ignored) { }
            }
        }
        return hash;        
    }
        
    public static boolean hasCustomHashCode(Class c)
    {   
        Class origClass = c;
        if (_customHash.containsKey(c))
        {
            return _customHash.get(c);
        }
        
        while (!Object.class.equals(c))
        {
            try
            {
                c.getDeclaredMethod("hashCode");
                _customHash.put(origClass, true);
                return true;
            }
            catch (Exception ignored) { }
            c = c.getSuperclass();
        }
        _customHash.put(origClass, false);
        return false;
    }
    
    /**
     * @param c Class instance
     * @return Collection of only the fields in the passed in class
     * that would need further processing (reference fields).  This
     * makes field traversal on a class faster as it does not need to
     * continually process known fields like primitives.
     */
    public static Collection<Field> getDeepDeclaredFields(Class c)
    {
        if (_reflectedFields.containsKey(c))
        {
            return _reflectedFields.get(c);
        }
        Collection<Field> fields = new ArrayList<Field>();
        Class curr = c;
        
        while (curr != null)
        {
            try 
            {
                Field[] local;
                local = curr.getDeclaredFields();
                
                for (Field field : local)
                {
                    if (!field.isAccessible())
                    {
                        try 
                        {
                            field.setAccessible(true);
                        }
                        catch (Exception ignored) { }
                    }
                    
                    int modifiers = field.getModifiers();
                    if (!Modifier.isStatic(modifiers) && !field.getName().startsWith("this$") && !Modifier.isTransient(modifiers))
                    {   // speed up: do not count static fields, not go back up to enclosing object in nested case    
                        fields.add(field);
                    }                                      
                }               
            }
            catch (ThreadDeath t)
            {
                throw t;
            }
            catch (Throwable ignored)
            { }

            curr = curr.getSuperclass();
        }
        _reflectedFields.put(c, fields);
        return fields;
    }            
}
