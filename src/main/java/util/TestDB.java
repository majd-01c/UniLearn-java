package util;

public class TestDB {
    public static void main(String[] args) {
        try {
            HibernateSessionFactory.getInstance();
            System.out.println("Hibernate init SUCCESS.");
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("Hibernate init FAILED.");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
