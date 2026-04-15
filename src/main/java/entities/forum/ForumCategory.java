package entities.forum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import static jakarta.persistence.GenerationType.IDENTITY;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "forum_category")
public class ForumCategory implements java.io.Serializable {

    private int id;
    private String name;
    private String description;
    private String icon;
    private int position;
    private byte isActive;
    private Timestamp createdAt;
    private Set<ForumTopic> forumTopics = new HashSet<>(0);

    public ForumCategory() {
    }

    public ForumCategory(int id, String name, int position, byte isActive, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public ForumCategory(int id, String name, String description, String icon, int position, byte isActive, Timestamp createdAt, Set<ForumTopic> forumTopics) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.position = position;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.forumTopics = forumTopics;
    }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Column(name = "name", nullable = false, length = 100)
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column(name = "description")
    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Column(name = "icon", length = 50)
    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    @Column(name = "position", nullable = false)
    public int getPosition() {
        return this.position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Column(name = "is_active", nullable = false)
    public byte getIsActive() {
        return this.isActive;
    }

    public void setIsActive(byte isActive) {
        this.isActive = isActive;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, length = 19)
    public Timestamp getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "forumCategory")
    public Set<ForumTopic> getForumTopics() {
        return this.forumTopics;
    }

    public void setForumTopics(Set<ForumTopic> forumTopics) {
        this.forumTopics = forumTopics;
    }

    // === Helper methods ===

    @Transient
    public boolean isActiveCategory() {
        return this.isActive == 1;
    }

    @Transient
    public int getTopicsCount() {
        return this.forumTopics != null ? this.forumTopics.size() : 0;
    }

    @Override
    public String toString() {
        return this.name != null ? this.name : "";
    }
}
