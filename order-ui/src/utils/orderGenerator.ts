import type { CreateOrderRequest, User } from '../api'

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

/**
 * A random existing user, a random food, quantity 1-5, unit price $5.00-$25.00 times quantity.
 * The Order Service rejects unknown customers (422), so this needs the real user list.
 */
export function randomOrder(users: User[]): CreateOrderRequest {
  if (users.length === 0) {
    throw new Error('No users to pick from')
  }
  const quantity = randomInt(1, 5)
  const unitCents = randomInt(500, 2500)
  return {
    customerId: users[randomInt(0, users.length - 1)].id,
    item: FOODS[randomInt(0, FOODS.length - 1)],
    quantity,
    totalPrice: (unitCents * quantity) / 100,
  }
}
