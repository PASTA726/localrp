package app.localrp;

/** v0.19.16: one user identity ("persona") the AI roleplays the user as. Multiple
 *  personas are allowed; a chat may follow the global default or pick its own. */
public class Persona {
    public String id = "";
    public String name = "";
    public String description = "";
    public String avatarPath = "";

    public Persona() {}

    public Persona(String id, String name, String description, String avatarPath) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.avatarPath = avatarPath;
    }
}
