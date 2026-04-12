package services;

import java.util.List;

public interface IService<T> {
    void add(T entity);

    void update(T entity);

    void delete(T entity);

    List<T> getALL();
}
