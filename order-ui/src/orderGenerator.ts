import type { CreateOrderRequest } from './api'

const FOODS = [
  'Margherita pizza',
  'Pepperoni pizza',
  'Cheeseburger',
  'Chicken burrito',
  'Pad thai',
  'Sushi platter',
  'Caesar salad',
  'Ramen',
  'Fish and chips',
  'Falafel wrap',
  'Butter chicken',
  'Beef tacos',
]

function randomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

/** Customer c-1..c-5, a random food, quantity 1-5, unit price $5.00-$25.00 times quantity. */
export function randomOrder(): CreateOrderRequest {
  const quantity = randomInt(1, 5)
  const unitCents = randomInt(500, 2500)
  return {
    customerId: `c-${randomInt(1, 5)}`,
    item: FOODS[randomInt(0, FOODS.length - 1)],
    quantity,
    totalPrice: (unitCents * quantity) / 100,
  }
}
