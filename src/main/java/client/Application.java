package client;

import entities.Animal;
import utils.manager.OrmManager;

import java.util.List;

public class Application {
    public static void main(String[] args) {
        OrmManager orm = OrmManager.get("H2.db");
        orm.prepareRepositoryFor(Animal.class);
        Animal animal = new Animal("alex", 23);
        Animal animal1 = new Animal("alexey", 26);
        orm.save(animal);
        orm.save(animal1);
        Animal updateForAnimal1 = new Animal("vitya", 23);
        int res = orm.update(updateForAnimal1);
        System.out.println(res);
        Animal animal2 = new Animal("alexey", 26);
        List<Animal> list = orm.getAll(Animal.class);
        for(var i : list){
            System.out.println(i.getName());
        }
    }
}
