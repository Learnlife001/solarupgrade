package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.model.Role;
import com.shoppingapp.shoppingwebapp.model.User;
import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who is an administrator is decided by one environment variable, and these
 * check both directions of that: naming an account grants the role, and
 * dropping it from the list takes it away again.
 */
@SpringBootTest
@Transactional
class AdminBootstrapTest {

    @Autowired
    private AdminBootstrap bootstrap;

    @Autowired
    private UserRepository users;

    private User account(String email) {
        return users.findByEmail(email)
                .orElseGet(() -> users.save(new User(email, "hash", "Someone")));
    }

    @Test
    void anAccountNamedInTheListBecomesAnAdmin() {
        User user = account("bootstrap-one@example.test");
        assertThat(user.getRole()).isEqualTo(Role.USER);

        bootstrap.apply(users, "bootstrap-one@example.test");

        assertThat(users.findByEmail("bootstrap-one@example.test").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    /** Case and spacing are what a person typing a list into a form gets wrong. */
    @Test
    void theListToleratesSpacingAndCapitals() {
        account("bootstrap-two@example.test");

        bootstrap.apply(users, "  Bootstrap-Two@Example.Test , ");

        assertThat(users.findByEmail("bootstrap-two@example.test").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    /**
     * Revoking has to be as easy as granting, or the variable stops being the
     * truth about who holds the role.
     */
    @Test
    void removingAnAddressTakesTheRoleBack() {
        account("bootstrap-three@example.test");
        bootstrap.apply(users, "bootstrap-three@example.test");
        assertThat(users.findByEmail("bootstrap-three@example.test").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);

        bootstrap.apply(users, "somebody-else@example.test");

        assertThat(users.findByEmail("bootstrap-three@example.test").orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    /** An empty setting must not silently strip an existing administrator. */
    @Test
    void anEmptySettingChangesNothing() {
        User user = account("bootstrap-four@example.test");
        bootstrap.apply(users, "bootstrap-four@example.test");

        bootstrap.apply(users, "");

        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    /** Naming an address nobody has registered is a warning, not a crash. */
    @Test
    void anUnknownAddressIsIgnored() {
        bootstrap.apply(users, "never-registered@example.test");

        assertThat(users.findByEmail("never-registered@example.test")).isEmpty();
    }
}
