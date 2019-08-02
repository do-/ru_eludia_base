package ru.eludia.base.db.util;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import ru.eludia.base.DB;

public class TypeConverter {
    
    private static final Logger logger = Logger.getLogger (TypeConverter.class.getName ());

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray ();
    private final static Pattern datePattern = Pattern.compile("\\d{2}.\\d{2}.\\d{4}");
    static DatatypeFactory dtf;        
    
    static {
                
        try {
            dtf = DatatypeFactory.newInstance ();
        }
        catch (DatatypeConfigurationException ex) {
            throw new IllegalStateException ("Cannot create DatatypeFactory", ex);
        }

    }
    
    private static final Map<Class, Class> primitiveWrapperMap = new HashMap<>();
    static {
         primitiveWrapperMap.put(Boolean.TYPE, Boolean.class);
         primitiveWrapperMap.put(Byte.TYPE, Byte.class);
         primitiveWrapperMap.put(Character.TYPE, Character.class);
         primitiveWrapperMap.put(Short.TYPE, Short.class);
         primitiveWrapperMap.put(Integer.TYPE, Integer.class);
         primitiveWrapperMap.put(Long.TYPE, Long.class);
         primitiveWrapperMap.put(Double.TYPE, Double.class);
         primitiveWrapperMap.put(Float.TYPE, Float.class);
    }
    
    /**
     * Не-null строка из произвольного объекта
     * @param o что угодно
     * @return "" для null, o.toString () для прочих
     */
    public final static String String (Object o) {
        if (o == null) return "";
        if (o instanceof Boolean) return (Boolean) o ? "1" : "0"; 
        return o.toString ();
    }
    
    /**
     * long-значение объекта любого подходящего типа
     * @param o что угодно
     * @return 0 для null, пустой строки ("") и false; 1 для true; эквивалент Long.parseLong (o.toString ()) для прочих
     * @throws NumberFormatException если совсем никак
     */
    public final static long Long (Object o) {
        
        if (o == null) return 0L;
        
        if (DB.isLongValue (o)) return ((Number) o).longValue ();

        if (o instanceof Boolean) return (Boolean) o ? 1L : 0L;

        String s = o.toString ();
        
        switch (s.length ()) {
            
            case 0: return 0L;
            
            case 1: switch (s.charAt (0)) {
                case '0': return 0L;
                case '1': return 1L;
                case '2': return 2L;
                case '3': return 3L;
                case '4': return 4L;
                case '5': return 5L;
                case '6': return 6L;
                case '7': return 7L;
                case '8': return 8L;
                case '9': return 9L;
                default: throw new NumberFormatException ("Not a long: '" + s + "'");
            }
            
            default: return Long.parseLong (s);
 
        }
        
    }

    /**
     * Логическое значение для произвольного объекта в логичечком контексте,
     * по аналогии с Per5, js и прочими аналогичными языками.
     * @param o что угодно
     * @return false для:
     * * null;
     * * целого нуля (0) любого типа (в том числе BigDecimal)
     * * строк: 
     * ** "" (пустая строка)
     * ** "0"
     * ** " " (пробел)
     * ** "N" / "n"
     * ** "F" / "f" / "False" / "false"
     * ** "null"
     * и true для всех прочих значений, в том числе дробных 0.0.
     */
    public static final boolean Boolean (Object o) {

        if (o == null) return false;
        
        if (o instanceof Boolean) return (Boolean) o;

        if (DB.isLongValue (o)) return 0L != ((Number) o).longValue ();

        final String s = o.toString ();        
        int length = s.length ();
        
        switch (length) {
            
            case 0: return false;
            
            case 1: switch (s.charAt (0)) {
                case '0':
                case 'N':
                case 'n':
                case 'F':
                case 'f':
                case ' ':
                    return false;
                default: 
                    return true;
            }
            
            case 4: return !("null".equals (s));

            case 5: return !("false".equals (s) || "False".equals (s));
            
            default: 
                return true;

        }
        
    }
    
    /**
     * Преобразует произвольный список Map'ов со строковыми ключами в спсиок JSON, открытый для дозаписи
     * @param l исходный список (полученный DB.HASH)
     * @return тот же список в JSON
     */
    public final static JsonArrayBuilder JsonArrayBuilder (List<Map<String, Object>> l) {
        JsonArrayBuilder ab = Json.createArrayBuilder ();
        l.forEach (map -> ab.add (JsonObjectBuilder (map)));
        return ab;
    }

    /**
     * Преобразует произвольный Map со строковыми ключами в объект JSON, открытый для дозаписи
     * @param m набор пар ключ/значение
     * @return тот же набор, но в JSON и за исключением пустых значений
     */
    public final static JsonObjectBuilder JsonObjectBuilder (Map<String, Object> m) {
        
        JsonObjectBuilder job = Json.createObjectBuilder ();
               
        m.entrySet ().forEach (kv -> {
            
            Object value = kv.getValue ();
            
            if (value == null) return;
                        
            if (value instanceof JsonValue) {
                 job.add (kv.getKey (), (JsonValue) value);
            }
            else if (value instanceof Integer) {
                 job.add (kv.getKey (), (Integer) value);
            }
            else {
                 job.add (kv.getKey (), value.toString ());
            }
            
        });
        
        return job;
        
    }
        
    /**
     * Преобразует объект JSON в соответствующий Map со строковыми ключами,
     * пригодный для db.insert, db.update и т. п. вызовов.
     * Ключи приводятся к нижнему регистру. Логические значения заменяются на 0/1.
     * @param o объект JSON
     * @return те же данные в виде Map<String, Object>
     */
    public final static Map<String, Object> Map (JsonObject o) {
        
        Map<java.lang.String, Object> hash = DB.HASH ();
               
        o.entrySet ().forEach (kv -> {
            
            java.lang.String key = kv.getKey ().toLowerCase ();
            JsonValue value = kv.getValue ();                        
            
            if (value == JsonValue.NULL) {
                hash.put (key, null);
            }
            if (value == JsonValue.TRUE) {
                hash.put (key, 1);
            }
            else if (value == JsonValue.FALSE) {
                hash.put (key, 0);
            }
            else if (value instanceof JsonString) {
                hash.put (key, ((JsonString) value).getString ());
            }
            else if (value instanceof JsonNumber) {
                hash.put (key, ((JsonNumber) value).bigDecimalValue ());
            }

        });
        
        return hash;
        
    }
    
    /**
     * Преобразует объект в соответствующий Map со строковыми ключами
     * Ключи приводятся к нижнему регистру.
     * @param o объект
     * @return те же данные в виде Map<String, Object>
     */
    public final static Map<String, Object> Map(Object o) {

		Map<String, Object> hash = new HashMap<>();

		if (o == null)
			return hash;

		try {
			Class<?> clazz = o.getClass();
			while (clazz != null) {
				for (Field field : clazz.getDeclaredFields()) {
					if (!field.isAccessible()) {
						field.setAccessible(true);
					}
					hash.put(field.getName().toLowerCase(), field.get(o));
				}
				clazz = clazz.getSuperclass();
			}
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}

		return hash;

	}
    
    
    /**
     * Конструктор UUID из HEX-строки без разделителей
     * @param s Строка из 32 шестнадцатеричних цифр
     * @return Соответствующий UUID
     */
    public final static UUID UUIDFromHex (String s) {
        return UUID (bytesFromHex (s));
    }
    
    /**
     * Конструктор UUID, который забыли включить в J2SE
     * @param bytes Ровно 16 байт. Любые.
     * @return Соответствующий UUID
     */
    public final static UUID UUID (byte[] bytes) {
        
        if (bytes.length != 16) throw new IllegalArgumentException();
        int i = 0;
        long msl = 0;
        for (; i < 8; i++) msl = (msl << 8) | (bytes[i] & 0xFF);
        long lsl = 0;
        for (; i < 16; i++) lsl = (lsl << 8) | (bytes[i] & 0xFF);
        return new UUID(msl, lsl);
        
    }    
    
    /**
     * Перевод даты/времени из JDBC в JAXB.
     * @param ts дата/время из JDBC
     * @return дата/время для JAXB
     */
    public final static XMLGregorianCalendar XMLGregorianCalendar (Timestamp ts) {
        GregorianCalendar gc = new GregorianCalendar ();
        gc.setTime (new java.util.Date (ts.getTime ()));
        return dtf.newXMLGregorianCalendar (gc);
    }
    
    /**
     * Конструктор JAXB-дат из строк
     * @param s Строка с датой в XML-формате
     * @return Дата в виде, пригодном для использования в JAXB
     */
    public final static XMLGregorianCalendar XMLGregorianCalendar (String s) {
        return dtf.newXMLGregorianCalendar (s);
    }
    
    /**
     * Шестнадцатеричное представление бинарных данных
     * @param bytes массив байт
     * @return строка, изображающая исходный массив в шестнадцатеричном представлении (1 байт на входе = 2 цифры 0-F на выходе)
     */
    public static final String hex (byte[] bytes) {
        char [] hexChars = new char [bytes.length * 2];
        for (int j = 0; j < bytes.length; j ++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String (hexChars);
    }  
    
    /**
     * Шестнадцатеричное представление бинарных данных
     * @param s строка, изображающая исходный массив в шестнадцатеричном представлении
     * @return bytes массив байт (2 цифры 0-F на входе = 1 байт на выходе)
     */
    public static byte[] bytesFromHex (String s) {        
        int len = s.length ();        
        byte[] data = new byte [len / 2];        
        for (int i = 0; i < len; i += 2) {
            data [i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }        
        return data;        
    }
    
    /**
     * Вспомогательный метод для передачи бинарных данных в BLOB-параметры
     * @param v массив байт
     * @return представляющий его поток
     */
    public static final InputStream binaryStream (Object v) {

        if (v instanceof byte []) return new java.io.ByteArrayInputStream ((byte []) v);
        
        return (InputStream) v;
        
    }
    
    public static final Timestamp timestamp (Object v) {
        
        if (v instanceof XMLGregorianCalendar) return new Timestamp (((XMLGregorianCalendar) v).toGregorianCalendar ().getTimeInMillis ());
        if (v instanceof java.util.Date) return new Timestamp (((java.util.Date) v).getTime ());

        String s = v.toString ();        
        
        int len = s.length ();
        
        if (len < 10) throw new IllegalArgumentException ("Invalid date: '" + s + "'");
        
        if (s.charAt (len - 1) == 'Z') {
            s = s.substring (0, len - 1);
        }
        else if (s.charAt (len - 3) == ':') {
            switch (s.charAt (len - 6)) {
                case '+':
                case '-':
                    s = s.substring (0, len - 6);
                break;
            }
        }
    
        if (s.length () < 10) throw new IllegalArgumentException ("Invalid date: '" + s + "'");
        
        if (s.length () == 10) {
            if (datePattern.matcher(s).matches()) {
                String[] dateParts = s.split("\\.");
                s = dateParts[2].concat("-").concat(dateParts[1]).concat("-").concat(dateParts[0]);
            }
            return Timestamp.valueOf (s + " 00:00:00");
        }
        
        if (s.charAt (10) == ' ') return Timestamp.valueOf (s);
        
        return Timestamp.valueOf (s.replace ('T', ' '));        
        
    }
    
    /**
     * Восстанавливает JSON-объект из его сериализованной формы
     * @param s Корректный JSON, начинающийся с '{'.
     * @return Распакованный объект
     */
    public static final JsonObject JsonObject (String s) {
        
        try (StringReader sr = new StringReader (s)) {
            
            try (JsonReader jr = Json.createReader (sr)) {
                
                return jr.readObject ();
                
            }

        }
        
    }
    
    /**
     * Переводит значение JSON в объект java.
     * @param v значение из 
     * @return Распакованный объект
     */
    public static final Object object (JsonValue v) {
        
        if (v == null) return null;
        
        switch (v.getValueType ()) {
            case NULL: return null;
            case TRUE: return 1;
            case FALSE: return 0;
            case STRING: return ((JsonString) v).getString ();
            case NUMBER: 
                JsonNumber n = (JsonNumber) v;
                return n.isIntegral () ? n.intValueExact () : n.bigDecimalValue ();
        }
        
        return null;
        
    }
    
    /**
     * Переводит запись, доступную как хэш "ключ-значение" 
     * (полученную, например, как DB.getMap) в объект javaBean.
     * 
     * А именно: создаётся пустой javaBean, а потом на каждое его свойство,
     * для которого находится соответствующая компонента map, вызывается setter.
     * 
     * @param clazz класс javaBean, экземпляр которого надо создать
     * @param values значения полей, которые надо установить
     * 
     * @return javaBean с требуемыми значениями полей
     */
    public static final <T> T javaBean (Class<T> clazz, Map<String, Object> values) {
                
        try {
            
            T javaBean = clazz.getConstructor ().newInstance ();
            
            BeanInfo info = Introspector.getBeanInfo (clazz);
            
            PropertyDescriptor [] props = info.getPropertyDescriptors();
            
            for (PropertyDescriptor pd: props) {

                String name = pd.getName ();

                Object value = values.get (name.toLowerCase ());
                
                if (value == null) continue;
                
                final Method writeMethod = pd.getWriteMethod ();

                if (writeMethod == null) {

                    final Method readMethod = pd.getReadMethod ();

                    if (!List.class.equals (readMethod.getReturnType ())) continue;

                    if (!(value instanceof List)) continue;

                    List list = (List)readMethod.invoke (javaBean);

                    list.addAll ((List) value);

                    continue;

                }

                Class<?> type = writeMethod.getParameterTypes () [0];
                                
                if (type.isPrimitive())
                    type = primitiveWrapperMap.get(type);
                
                if (String.class.equals (type)) {
                    final String s = value.toString ();
                    if (s.isEmpty ()) continue;
                    writeMethod.invoke (javaBean, s);
                } 
                else if (value.getClass().equals(type))
                    writeMethod.invoke(javaBean, value);
                else if (Boolean.class.equals (type)) {
                    final String s = value.toString ();
                    if (s.isEmpty ()) continue;
                    switch (s) {
                        case "1":
                        case "true":
                        case "TRUE":
                        case "True":
                            writeMethod.invoke (javaBean, true);
                            break;
                        default: 
                            writeMethod.invoke (javaBean, false);
                    }
                }
                else if (XMLGregorianCalendar.class.equals (type)) {
                    writeMethod.invoke (javaBean, XMLGregorianCalendar (value.toString ().replace (' ', 'T')));
                }
                else if (BigInteger.class.equals(type)) {
                        writeMethod.invoke (javaBean, new BigInteger (value.toString ()));
                }
                else if (BigDecimal.class.equals(type)) {
                        writeMethod.invoke (javaBean, new BigDecimal (value.toString ()));
                }
                else if (Byte.class.equals(type)
                        || Short.class.equals(type)
                        || Integer.class.equals(type)
                        || Long.class.equals(type)
                        || Double.class.equals(type)
                        || Float.class.equals(type)) {
                        writeMethod.invoke(javaBean, type.getMethod("valueOf", String.class).invoke(value, value.toString()));
                } else if (LocalDate.class.equals(type)){
                    writeMethod.invoke (javaBean, LocalDate.parse(value.toString().substring(0, 10)));
                } else if (LocalDateTime.class.equals(type)){
                    writeMethod.invoke (javaBean, LocalDateTime.parse(value.toString().replace (' ', 'T')));
                } else if (LocalTime.class.equals(type)){
                    writeMethod.invoke (javaBean, LocalTime.parse(value.toString().substring(11)));
                }
                else {
                    logger.warning ("javaBean property setting not supported for " + type.getName ());
                }
                    
            }
            
            return javaBean;
            
        }
        catch (Exception ex) {
ex.printStackTrace ();
            throw new IllegalStateException (ex);
        }
                
    }

    /**
     * Переводит объект javaBean с типом <b>S</b> в объект javaBean c типом <b>T</b>.
     * Копируются только параметры, наименования которых в классах совпадают
     *
     * @param clazz класс javaBean, экземпляр которого надо создать
     * @param source объект javaBean, значения полей которого надо скопировать
     * @param <T> целевой класс
     * @param <S> класс источника данных
     * @return javaBean с типом <b>S</b> и с требуемыми значениями полей
     */
    public static final <T, S> T javaBean (Class<T> clazz, S source) {

        if (source == null)
            return null;

        Class sourceClass = source.getClass();

        List<Field> sourceFields = new ArrayList<>();
        while (sourceClass != null) {
            sourceFields.addAll(Arrays.asList(sourceClass.getDeclaredFields()));
            sourceClass = sourceClass.getSuperclass();
        }

        try {
            T javaBean = clazz.getConstructor().newInstance();

            for (Field sourceField : sourceFields) {
                if (sourceField.getName().equals("serialVersionUID")) {
                    continue;
                }
                sourceField.setAccessible(true);
                Object value = sourceField.get(source);
                if (value == null)
                    continue;
                Field targetField = getField(clazz, sourceField.getName());
                if (targetField == null)
                    continue;
                if (Modifier.isFinal(targetField.getModifiers()))
                    continue;

                Class sourceFieldType = getFieldType(sourceField);
                Class targetFieldType = getFieldType(targetField);

                if (sourceFieldType.equals(targetFieldType)) {
                    if (!(sourceField.getGenericType() instanceof ParameterizedType)) {
                        setValue(javaBean, targetField, value);
                        continue;
                    }
                    if (!(targetField.getGenericType() instanceof ParameterizedType))
                        continue;
                    Class<?> sourceFieldGenericType =
                            (Class<?>) ((ParameterizedType) sourceField.getGenericType()).getActualTypeArguments()[0];
                    Class<?> targetFieldGenericType =
                            (Class<?>) ((ParameterizedType) targetField.getGenericType()).getActualTypeArguments()[0];
                    if (sourceFieldGenericType.equals(targetFieldGenericType)) {
                        setValue(javaBean, targetField, value);
                        continue;
                    }

                    if (sourceField.getType().equals(List.class)) {
                        List<Object> targetList = new ArrayList<>();
                        for (Object v : (List) value) {
                            Object targetGenericObj = javaBean(targetFieldGenericType, v);
                            targetList.add(targetGenericObj);
                        }
                        setValue(javaBean, targetField, targetList);
                    }
                } else {
                    Object targetFieldObj = javaBean(targetField.getType(), value);
                    setValue(javaBean, targetField, targetFieldObj);
                }
            }

            return javaBean;

        } catch (Exception ex) {
            ex.printStackTrace ();
            throw new IllegalStateException (ex);
        }

    }

    private static Field getField(Class clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null)
                return getField(clazz.getSuperclass(), fieldName);
        }
        return null;
    }

    private static <T> void setValue(T target, Field field, Object value) throws IllegalAccessException {
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Class getFieldType(Field field) {
        Class type = field.getType();
        if (type.isPrimitive())
            type = primitiveWrapperMap.get(type);
        return type;
    }

    private static JsonValue jsonNumber (Object o) {
        return Json.createArrayBuilder ().add (new BigDecimal (o.toString ())).build ().get (0);
    }
    
    private static JsonValue jsonString (String s) {
        return Json.createArrayBuilder ().add (s).build ().get (0);
    }

    public static JsonValue json (Object o) {
        
        if (o == null) return JsonValue.NULL;
        
        if (o instanceof Boolean) return Boolean.TRUE.equals (o) ? JsonValue.TRUE : JsonValue.FALSE;
        
        if (o instanceof Number) return jsonNumber (o);
        if (o instanceof BigDecimal) return jsonNumber (o);
        if (o instanceof BigInteger) return jsonNumber (o);
                
        if (o instanceof Collection) {
            JsonArrayBuilder ab = Json.createArrayBuilder ();
            for (Object i: (Collection) o) ab.add (json (i));
            return ab.build ();
        }
        
        if (o instanceof Map) {
            final JsonObjectBuilder ob = Json.createObjectBuilder ();
            final Map m = (Map) o;
            if (m.containsKey (null)) ob.add ("null", json (m.get (null)));
            m.keySet ().stream ().filter (t -> t != null).sorted ().forEach ((k) -> {
                ob.add (k.toString (), json (m.get (k)));
            });
            return ob.build ();
        }
        
        return jsonString (o.toString ());
        
    }
    
    public static Object pojo (JsonValue jv) {
        
        if (jv == null) return null;
        if (JsonValue.NULL.equals (jv)) return null;
        
        if (JsonValue.TRUE.equals (jv)) return 1;
        if (JsonValue.FALSE.equals (jv)) return 0;
        
        if (jv instanceof JsonNumber) {
            
            JsonNumber jn = (JsonNumber) jv;
            
            try {
                return jn.longValueExact ();
            }
            catch (ArithmeticException ex) {
                return jn.bigDecimalValue ();
            }
            
        }
        
        if (jv instanceof JsonArray) {
            
            JsonArray ja = (JsonArray) jv;
            
            switch (ja.size ()) {
                case 0:
                    return Collections.EMPTY_LIST;
                case 1:
                    return Collections.singletonList (pojo (ja.get (0)));
                default:
                    return ja.stream ().map (TypeConverter::pojo).collect (Collectors.toList ());
            }
                        
        }
        
        if (jv instanceof JsonObject) {
            
            JsonObject jo = (JsonObject) jv;
            final int size = jo.size ();
            
            switch (size) {
                case 0:
                    return Collections.EMPTY_MAP;
                case 1:
                    Map.Entry<java.lang.String, JsonValue> entry = jo.entrySet ().stream ().findFirst ().get ();
                    return Collections.singletonMap (entry.getKey (), pojo (entry.getValue ()));
                default:
                    Map<String, Object> m = new HashMap<> (size);
                    for (String k: jo.keySet ()) m.put (k, pojo (jo.get (k)));
                    return m;
            }               
            
        }
                
        throw new IllegalArgumentException ("Cannot translate to POJO: " + jv);
        
    }

    public static Map<String, Object> HASH (JsonObject jo) {
        return (Map<String, Object>) pojo (jo);
    }    

}