package utils.manager;

import utils.annotations.Column;
import utils.annotations.Entity;
import utils.annotations.Id;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class OrmManager {

    Connection connection;

    UnaryOperator<String> wrapInQuotes = s -> "'" + s + "'";

    public OrmManager(String database) {
        connection = ConnectionManager.open(database);
    }

    public static OrmManager get(String key) {
        return new OrmManager(key);
    }

    public <T> void prepareRepositoryFor(Class<T> clazz) {
        Entity entity = clazz.getAnnotation(Entity.class);
        if (Objects.nonNull(entity)) {
            StringBuilder builder = new StringBuilder("DROP TABLE IF EXISTS " + entity.value() + ";");
            builder.append("CREATE TABLE ").
                    append(entity.value()).
                    append("(");
            for (var field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    builder.append(readAnnotation(field, field.getAnnotation(Id.class)))
                            .append(" ")
                            .append(convertToSQLType(field.getType()))
                            .append(" PRIMARY KEY AUTO_INCREMENT,");
                }
                if (field.isAnnotationPresent(Column.class)) {
                    builder.append(readAnnotationColumn(field, field.getAnnotation(Column.class)))
                            .append(" ")
                            .append(convertToSQLType(field.getType()))
                            .append(",");
                }
            }
            builder.deleteCharAt(builder.length() - 1).append(");");
            runCommand(builder.toString());
        } else {
            throw new IllegalArgumentException("Obtained class without entity annotation ");
        }
    }

    public <T> void save(T object) {
        StringBuilder builder = new StringBuilder("INSERT INTO " + object.getClass().getSimpleName() + "(");
        var clazz = object.getClass();
        List<Object> values = new ArrayList<>();
        for (var field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Column.class)) {
                builder.append(readAnnotationColumn(field, field.getAnnotation(Column.class)))
                        .append(",");
                try {
                    values.add(field.get(object));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        builder.deleteCharAt(builder.length() - 1).append(") VALUES (");
        for (var value : values) {
            if (value instanceof String) {
                builder.append(wrapInQuotes.apply(value.toString())).append(",");
            } else {
                builder.append(value.toString()).append(",");
            }
        }
        builder.deleteCharAt(builder.length() - 1).append(");");
        try {
            Field primaryKey = getIdField(object);
            primaryKey.setAccessible(true);
            primaryKey.set(object, getIdfromDB(builder.toString()));

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private long getIdfromDB(String command) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(command, Statement.RETURN_GENERATED_KEYS);
            ResultSet resultSet = statement.getGeneratedKeys();

            String key;
            if (resultSet.next()) {
                key = resultSet.getString(1);
                return Long.parseLong(key);
            } else {
                throw new NoSuchElementException("Auto increment field can't be absent");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private <T> Field getIdField(T object) {
        for (var field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field;
            }
        }
        throw new RuntimeException("Id annotated field wasn't found.");
    }

    public <T> int update(T object) {
        if (object.getClass().isAnnotationPresent(Entity.class)) {
            StringBuilder builder = new StringBuilder("UPDATE " + object.getClass().getSimpleName() + " SET ");
            for (var field : object.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.isAnnotationPresent(Column.class)) {
                    builder.append(readAnnotationColumn(field, field.getAnnotation(Column.class))).append("=");
                    try {
                        var o = field.get(object);
                        if (o instanceof String) {
                            builder.append(wrapInQuotes.apply(o.toString())).append(",");
                        } else {
                            builder.append(o.toString()).append(",");
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException();
                    }
                }
            }
            builder.deleteCharAt(builder.length() - 1);
            Field primaryKey = getIdField(object);
            builder.append(" WHERE ")
                    .append(readAnnotation(primaryKey, primaryKey.getAnnotation(Id.class)))
                    .append(" = ");
            try {
                var id = object.getClass().getDeclaredField(primaryKey.getName());
                id.setAccessible(true);
                builder.append(Objects.toString(id.get(object))).append(";");
                try (Statement statement = connection.createStatement()) {
                    return statement.executeUpdate(builder.toString());
                } catch (SQLException e) {
                    throw new RuntimeException("Command execute error");
                }
            } catch (Exception e) {
                throw new RuntimeException("Id retrieving was denied");
            }
        } else {
            throw new IllegalArgumentException("Obtained class without entity annotation ");
        }
    }

    public <T> List<T> getAll(Class<T> clazz) {
        String query = "SELECT * FROM " + clazz.getSimpleName() + ";";
        List<T> resultList = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                T object = clazz.newInstance();
                var fields = object.getClass().getDeclaredFields();

                for (var field : fields) {
                    field.setAccessible(true);
                    if (field.getType() == String.class) {
                        field.set(object, resultSet.getString(readAnnotationColumn(field, field.getAnnotation(Column.class))));
                    } else if (field.isAnnotationPresent(Id.class) || field.getType() == Long.class) {
                        field.set(object, Long.valueOf(resultSet.getInt(readAnnotation(field, field.getAnnotation(Id.class)))));
                    } else if (field.getType() == int.class) {
                        field.set(object, resultSet.getInt(readAnnotationColumn(field, field.getAnnotation(Column.class))));
                    }
                }
                resultList.add(object);
            }
        } catch (SQLException e) {
            throw new RuntimeException();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    private <T> String convertToSQLType(Class<T> field) {
        var key = field.getSimpleName();
        switch (key) {
            case "String":
                return "VARCHAR(50)";
            case "int":
                return "INTEGER";
            case "Long":
                return "BIGINT";
            default:
                throw new IllegalArgumentException();
        }
    }

    private void runCommand(String command) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(command);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private String readAnnotation(Field field, Id id) {

        return (Objects.equals((id).value(), "") ? field.getName() : id.value()).toUpperCase();
    }

    private String readAnnotationColumn(Field field, Column column) {
        return (Objects.equals((column).value(), "") ? field.getName() : column.value()).toUpperCase();
    }
}
