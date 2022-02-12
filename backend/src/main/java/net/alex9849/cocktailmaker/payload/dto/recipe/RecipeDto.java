package net.alex9849.cocktailmaker.payload.dto.recipe;

import lombok.*;
import net.alex9849.cocktailmaker.model.recipe.AddIngredientsProductionStep;
import net.alex9849.cocktailmaker.model.recipe.ProductionStepIngredient;
import net.alex9849.cocktailmaker.model.recipe.Recipe;
import net.alex9849.cocktailmaker.payload.dto.category.CategoryDto;
import net.alex9849.cocktailmaker.payload.dto.recipe.ingredient.IngredientDto;
import net.alex9849.cocktailmaker.payload.dto.recipe.productionstep.ProductionStepDto;
import org.springframework.beans.BeanUtils;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RecipeDto {
    private interface Id { long getId(); }
    private interface Name { @NotNull @Size(min = 1, max = 30) String getName(); }
    private interface Description { @NotNull @Size(min = 1, max = 3000) String getDescription(); }
    private interface ProductionStepsCreated { @NotNull @NotEmpty List<ProductionStepDto.Request.Create> getProductionSteps(); }
    private interface ProductionStepsDetailed { @NotNull @NotEmpty List<ProductionStepDto.Response.Detailed> getProductionSteps(); }
    private interface Categories { @NotNull Set<CategoryDto> getCategories(); }
    private interface DefaultAmountToFill { @NotNull @Min(50) long getDefaultAmountToFill(); }

    private interface OwnerName { String getOwnerName(); }
    private interface HasImage { boolean isHasImage(); }
    private interface UniqueIngredients { Set<IngredientDto.Response.Reduced> getIngredients(); };
    private interface LastUpdate { Date getLastUpdate(); }
    private interface OwnerId { long getOwnerId(); }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Request {
        @Getter @Setter @EqualsAndHashCode(callSuper = true)
        public static class Create implements Name, OwnerId, Description, ProductionStepsCreated, Categories, DefaultAmountToFill {
            String name;
            long ownerId;
            String description;
            List<ProductionStepDto.Request.Create> productionSteps;
            Set<CategoryDto> categories;
            long defaultAmountToFill;
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Response {

        @Getter @Setter @EqualsAndHashCode(callSuper = true)
        public static class Detailed implements Name, OwnerId, Description, ProductionStepsDetailed, Categories,
                HasImage, DefaultAmountToFill, LastUpdate {
            final long id;
            String name;
            long ownerId;
            String description;
            List<ProductionStepDto.Response.Detailed> productionSteps;
            Set<CategoryDto> categories;
            boolean hasImage;
            long defaultAmountToFill;
            Date lastUpdate;

            public Detailed(Recipe recipe) {
                this.id = recipe.getId();
                BeanUtils.copyProperties(recipe, this);
                this.productionSteps = recipe.getProductionSteps().stream()
                        .map(ProductionStepDto.Response.Detailed::toDto).collect(Collectors.toList());
                this.categories = recipe.getCategories().stream().map(CategoryDto::new)
                        .collect(Collectors.toSet());
            }
        }

        @Getter @Setter @EqualsAndHashCode(callSuper = true)
        public static class SearchResult implements Id, Name, OwnerName, Description, HasImage, UniqueIngredients {
            final long id;
            String name;
            String ownerName;
            String description;
            boolean hasImage;
            Set<IngredientDto.Response.Reduced> ingredients;

            public SearchResult(Recipe recipe) {
                this.id = recipe.getId();
                BeanUtils.copyProperties(recipe, this);
                this.setOwnerName(recipe.getOwner().getUsername());
                this.ingredients = recipe.getProductionSteps().stream()
                        .filter(x -> x instanceof AddIngredientsProductionStep)
                        .flatMap(x -> ((AddIngredientsProductionStep) x).getStepIngredients().stream())
                        .map(ProductionStepIngredient::getIngredient)
                        .map(IngredientDto.Response.Reduced::toDto)
                        .collect(Collectors.toSet());
            }
        }
    }
}
