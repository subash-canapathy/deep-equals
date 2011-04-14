package com.software.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
            boolean key1Equal = false;
            if (_key1 == null || that._key1 == null)
            {
                key1Equal = _key1 == that._key1;
            }
            else
            {
                key1Equal = _key1.equals(that._key1);
            }
            
            if (!key1Equal)
            {
                return false;
            }
            
            boolean key2Equal = false;
            if (_key2 == null || that._key2 == null)
            {
                key2Equal = _key2 == that._key2;
            }
            else
            {
                key2Equal = _key2.equals(that._key2);
            }
            
            return key2Equal;
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
                    DualKey dk = new DualKey();
                    dk._key1 = Array.get(dualKey._key1, i);
                    dk._key2 = Array.get(dualKey._key2, i);
                    
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
                    DualKey dk = new DualKey();
                    dk._key1 = field.get(dualKey._key1);
                    dk._key2 = field.get(dualKey._key2);
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
                    
                    if (!Modifier.isStatic(field.getModifiers()) && !field.getName().startsWith("this$"))
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
