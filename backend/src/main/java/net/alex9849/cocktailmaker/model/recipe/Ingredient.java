package net.alex9849.cocktailmaker.model.recipe;

import net.alex9849.cocktailmaker.model.Pump;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "ingredients", uniqueConstraints = {@UniqueConstraint(columnNames = "name")})
public class Ingredient implements Serializable {

    static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min = 1, max = 30)
    private String name;

    @NotNull
    @Min(0) @Max(100)
    private int alcoholContent;

    @OneToMany(mappedBy = "ingredient", fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<RecipeIngredient> recipeIngredients;

    @OneToMany(mappedBy = "currentIngredient", fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<Pump> recipePumps;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAlcoholContent() {
        return alcoholContent;
    }

    public void setAlcoholContent(int alcoholContent) {
        this.alcoholContent = alcoholContent;
    }

    public static class IngredientFilterNoFilter implements Specification<Ingredient> {

        @Override
        public Predicate toPredicate(Root<Ingredient> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
            return criteriaBuilder.and();
        }
    }

    public static class IngredientFilterStartsWith implements Specification<Ingredient> {
        private String startsWith;
        private boolean ignoreCase;

        public IngredientFilterStartsWith(String startwith, boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            this.startsWith = startwith;
        }

        @Override
        public Predicate toPredicate(Root<Ingredient> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
            if(ignoreCase) {
                return criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), startsWith.toLowerCase() + "%");
            } else {
                return criteriaBuilder.like(root.get("name"), startsWith + "%");
            }
        }
    }
}
