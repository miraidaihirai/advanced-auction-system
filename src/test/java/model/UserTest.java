
package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    // Class con dùng để kiểm thử abstract User
    private static class TestUser extends User {

        TestUser(String id, String name, String password) {
            super(id, name, password);
        }

        @Override
        public void displayInfo() {
            // Không cần kiểm thử ở đây
        }
    }

    @Test
    void constructor_shouldInitializeFields() {
        TestUser user = new TestUser(
                "USER-01",
                "Bob",
                "password123"
        );

        assertEquals("USER-01", user.getId());
        assertEquals("Bob", user.getName());
        assertEquals("password123", user.getPassword());
    }

    @Test
    void constructor_withNullId_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestUser(null, "Bob", "password123")
        );
    }

    @Test
    void constructor_withBlankId_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestUser("   ", "Bob", "password123")
        );
    }

    @Test
    void constructor_withNullName_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestUser("USER-01", null, "password123")
        );
    }

    @Test
    void constructor_withBlankName_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestUser("USER-01", "   ", "password123")
        );
    }

    @Test
    void constructor_withNullPassword_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestUser("USER-01", "Bob", null)
        );
    }

    @Test
    void constructor_withBlankPassword_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestUser("USER-01", "Bob", "   ")
        );
    }

    @Test
    void constructor_withShortPassword_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestUser("USER-01", "Bob", "1234567")
        );
    }

    @Test
    void constructor_withEightCharacterPassword_shouldSucceed() {
        TestUser user = new TestUser(
                "USER-01",
                "Bob",
                "12345678"
        );

        assertEquals("12345678", user.getPassword());
    }

    @Test
    void setName_shouldUpdateName() {
        TestUser user = createUser();

        user.setName("Alice");

        assertEquals("Alice", user.getName());
    }

    @Test
    void setName_withInvalidValue_shouldThrowException() {
        TestUser user = createUser();

        assertThrows(
                IllegalArgumentException.class,
                () -> user.setName(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> user.setName("   ")
        );

        assertEquals("Bob", user.getName());
    }

    @Test
    void setPassword_shouldUpdatePassword() {
        TestUser user = createUser();

        user.setPassword("newpass8");

        assertEquals("newpass8", user.getPassword());
    }

    @Test
    void setPassword_withInvalidValue_shouldThrowException() {
        TestUser user = createUser();

        assertThrows(
                IllegalArgumentException.class,
                () -> user.setPassword(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> user.setPassword("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> user.setPassword("1234567")
        );

        assertEquals("password123", user.getPassword());
    }

    private TestUser createUser() {
        return new TestUser(
                "USER-01",
                "Bob",
                "password123"
        );
    }
}
