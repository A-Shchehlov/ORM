package entities;

import utils.annotations.Column;
import utils.annotations.Entity;
import utils.annotations.Id;

@Entity("Animal")
public class Animal {
    public Animal() {
    }

    public Animal(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public Animal(Long id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    @Id
    private Long id;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column(value = "Fullname")
    private String name;

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Column(value = "Age")
    private int age;
}
