/**
 * Patrón Builder - Construye objetos complejos paso a paso
 */
public class Person {
    
    private final String firstName;
    private final String lastName;
    private final int age;
    private final String email;
    private final String phone;
    private final String address;
    
    private Person(Builder builder) {
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.age = builder.age;
        this.email = builder.email;
        this.phone = builder.phone;
        this.address = builder.address;
    }
    
    public static class Builder {
        private final String firstName;
        private final String lastName;
        private int age;
        private String email;
        private String phone;
        private String address;
        
        public Builder(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
        
        public Builder age(int age) {
            this.age = age;
            return this;
        }
        
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }
        
        public Builder address(String address) {
            this.address = address;
            return this;
        }
        
        public Person build() {
            return new Person(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Person{name='%s %s', age=%d, email='%s', phone='%s', address='%s'}",
            firstName, lastName, age, email, phone, address);
    }
    
    public static void main(String[] args) {
        Person person1 = new Person.Builder("Juan", "Pérez")
            .age(30)
            .email("juan@example.com")
            .phone("555-1234")
            .address("Calle Principal 123")
            .build();
        
        Person person2 = new Person.Builder("María", "García")
            .age(25)
            .email("maria@example.com")
            .build();
        
        System.out.println(person1);
        System.out.println(person2);
    }
}